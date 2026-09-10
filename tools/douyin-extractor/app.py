from __future__ import annotations

import argparse
import asyncio
import copy
import json
import mimetypes
import os
import queue
import secrets
import subprocess
import sys
import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, unquote, urlparse

from extractor import ExtractError, extract_and_download, share_url

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "downloads"
PROFILE = ROOT / ".browser-profile"
TOKEN = secrets.token_urlsafe(32)
JOBS, LOCK, QUEUE = {}, threading.Lock(), queue.Queue(maxsize=3)
ACTIVE_STATES = {"queued", "opening", "extracting", "downloading"}


def snapshot(job_id=None):
    with LOCK:
        data = JOBS.get(job_id) if job_id else list(reversed(list(JOBS.values())))
        return copy.deepcopy(data)


def run_worker():
    while True:
        job_id = QUEUE.get()

        def update(state, message, **extra):
            with LOCK:
                JOBS[job_id].update(state=state, message=message, **extra)

        def cancelled():
            with LOCK:
                return JOBS[job_id].get("cancel", False)

        try:
            if cancelled():
                raise ExtractError("任务已取消")
            result = asyncio.run(extract_and_download(snapshot(job_id)["url"], PROFILE, OUTPUT / job_id, update, cancelled))
            state = "partial" if result["failures"] else "done"
            message = f"已保存 {len(result['files'])} 个文件" + (f"，{len(result['failures'])} 个失败，详情见 metadata.json" if result["failures"] else "")
            update(state, message, title=result["title"], files=result["files"], source_url=result["source_url"])
        except Exception as exc:
            message = str(exc) if isinstance(exc, ExtractError) else f"处理失败（{type(exc).__name__}），请检查网络并重试。"
            update("cancelled" if cancelled() else "error", message)
        finally:
            QUEUE.task_done()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # Keep share tokens and local access tokens out of logs.

    def reply(self, status, data, content_type="application/json; charset=utf-8"):
        body = json.dumps(data, ensure_ascii=False).encode() if not isinstance(data, bytes) else data
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.end_headers()
        self.wfile.write(body)

    def local_request(self):
        expected = f"127.0.0.1:{self.server.server_port}"
        if self.headers.get("Host") != expected:
            self.reply(403, {"error": "仅支持本机 127.0.0.1 访问"})
            return False
        return True

    def authenticated(self):
        if not self.local_request():
            return False
        origin = self.headers.get("Origin")
        if origin and origin != f"http://127.0.0.1:{self.server.server_port}":
            self.reply(403, {"error": "不允许跨站请求"})
            return False
        if not secrets.compare_digest(self.headers.get("X-Local-Token", ""), TOKEN):
            self.reply(403, {"error": "请刷新本地页面后重试"})
            return False
        return True

    def do_GET(self):
        if not self.local_request():
            return
        path = urlparse(self.path).path
        if path == "/":
            body = (ROOT / "index.html").read_text(encoding="utf-8").replace("__LOCAL_TOKEN__", TOKEN).encode()
            return self.reply(200, body, "text/html; charset=utf-8")
        if path == "/api/jobs":
            if self.authenticated():
                return self.reply(200, {"jobs": snapshot(), "output": str(OUTPUT)})
            return
        if path.startswith("/files/"):
            if self.headers.get("Sec-Fetch-Site") == "cross-site":
                return self.reply(403, {"error": "不允许跨站访问"})
            candidate = (OUTPUT / unquote(path[len("/files/"):])).resolve()
            if not candidate.is_relative_to(OUTPUT.resolve()) or not candidate.is_file() or candidate.suffix == ".part":
                return self.reply(404, {"error": "文件不存在"})
            self.send_response(200)
            self.send_header("Content-Type", mimetypes.guess_type(candidate.name)[0] or "application/octet-stream")
            self.send_header("Content-Length", str(candidate.stat().st_size))
            self.send_header("Content-Disposition", f"attachment; filename*=UTF-8''{quote(candidate.name)}")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            try:
                with candidate.open("rb") as file:
                    while chunk := file.read(128 * 1024):
                        self.wfile.write(chunk)
            except (BrokenPipeError, ConnectionResetError):
                pass
            return
        self.reply(404, {"error": "页面不存在"})

    def do_POST(self):
        if not self.authenticated():
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 16000:
                raise ExtractError("请求长度无效")
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict):
                raise ExtractError("请求必须是 JSON 对象")
            if self.path == "/api/jobs":
                text = body.get("text")
                if not isinstance(text, str):
                    raise ExtractError("请输入分享链接")
                url = share_url(text)
                with LOCK:
                    if sum(j["state"] in ACTIVE_STATES for j in JOBS.values()) >= 3:
                        raise ExtractError("最多同时保留 3 个待处理任务，请等待或取消已有任务。")
                    job_id = secrets.token_hex(8)
                    JOBS[job_id] = {"id": job_id, "url": url, "state": "queued", "message": "等待处理", "files": []}
                    QUEUE.put_nowait(job_id)
                return self.reply(202, {"id": job_id})
            if self.path == "/api/cancel":
                with LOCK:
                    job = JOBS.get(str(body.get("id")))
                    if job and job["state"] in ACTIVE_STATES:
                        job["cancel"] = True
                        job["message"] = "正在取消；网络请求结束后生效…"
                return self.reply(200, {"ok": True})
            if self.path == "/api/open-folder":
                OUTPUT.mkdir(exist_ok=True)
                if sys.platform == "darwin":
                    subprocess.Popen(["open", str(OUTPUT)])
                elif sys.platform == "win32":
                    os.startfile(str(OUTPUT))
                else:
                    subprocess.Popen(["xdg-open", str(OUTPUT)])
                return self.reply(200, {"ok": True})
            self.reply(404, {"error": "接口不存在"})
        except (ValueError, ExtractError) as exc:
            self.reply(400, {"error": str(exc)})
        except Exception:
            self.reply(500, {"error": "操作失败，请稍后重试"})


def main():
    parser = argparse.ArgumentParser(description="抖音视频／图集提取工具")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--no-open", action="store_true", help="不自动打开操作页面")
    args = parser.parse_args()
    try:
        server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    except OSError:
        raise SystemExit("端口被占用，可使用 python app.py --port 8766 更换端口。")
    threading.Thread(target=run_worker, daemon=True).start()
    url = f"http://127.0.0.1:{server.server_port}"
    print(f"抖音提取工具已启动：{url}\n文件保存到：{OUTPUT}\n按 Ctrl+C 停止。", flush=True)
    if not args.no_open:
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        with LOCK:
            for job in JOBS.values():
                job["cancel"] = True
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
