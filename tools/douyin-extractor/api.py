"""Authenticated API for single-post parsing and queued media downloads."""
from __future__ import annotations

import argparse
import copy
import json
import os
import secrets
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field

from direct_api import APIError, load_session, parse
from extractor import ExtractError, download_asset, media_opener, share_url

ROOT = Path(__file__).resolve().parent
ACTIVE = {"queued", "parsing", "downloading"}


class ParseRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    text: str = Field(min_length=1, max_length=8192, description="抖音作品分享文本或完整视频／图集链接")


class Media(BaseModel):
    kind: str
    urls: list[str]


class ParseResult(BaseModel):
    id: str
    title: str
    author: str
    type: str
    source_url: str
    provider: str
    parsed_at: int
    assets: list[Media]


class ParseResponse(BaseModel):
    data: ParseResult
    notice: str = "媒体直链可能过期或需要请求头；需要稳定文件时请创建下载任务。"


def saved_key(path: Path) -> str:
    if key := os.environ.get("DOUYIN_API_KEY"):
        return key
    try:
        with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as f:
            f.write(secrets.token_urlsafe(32))
    except FileExistsError:
        pass
    key = path.read_text().strip()
    if len(key) < 24:
        raise RuntimeError("API 密钥至少需要 24 个字符")
    return key


class Runtime:
    def __init__(self, session: Path, output: Path, key: str, proxy=None, parser=parse):
        if len(key) < 24:
            raise ValueError("API 密钥至少需要 24 个字符")
        self.session, self.output, self.key, self.proxy = session, output, key, proxy
        self.parser = parser
        self.jobs = {}
        self.lock = threading.RLock()
        self.upstream = threading.Lock()
        self.stop = threading.Event()
        self.executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="douyin-download")
        self.output.mkdir(parents=True, exist_ok=True)
        # Only local status and filenames are persisted, never cookies or signed URLs.
        for path in self.output.glob("*/job.json"):
            try:
                job = json.loads(path.read_text())
                if job["id"] != path.parent.name:
                    continue
                if job["state"] in ACTIVE:
                    job.update(state="error", error={"code": "INTERRUPTED", "message": "服务重启中断了任务，请重新提交。"})
                    path.write_text(json.dumps(job, ensure_ascii=False))
                self.jobs[job["id"]] = job
            except (OSError, ValueError, KeyError):
                continue

    def update(self, job_id, **fields):
        with self.lock:
            self.jobs[job_id].update(**fields, updated_at=int(time.time()))
            folder = self.output / job_id
            folder.mkdir(exist_ok=True)
            temp = folder / "job.tmp"
            temp.write_text(json.dumps(self.jobs[job_id], ensure_ascii=False))
            temp.replace(folder / "job.json")

    def job(self, job_id):
        with self.lock:
            if job_id not in self.jobs:
                raise APIError("NOT_FOUND", "任务不存在。", 404)
            return copy.deepcopy(self.jobs[job_id])

    def enqueue(self, text):
        try:
            url = share_url(text)
        except ExtractError as exc:
            raise APIError("INVALID_URL", str(exc), 400) from None
        load_session(self.session)
        with self.lock:
            if sum(j["state"] in ACTIVE for j in self.jobs.values()) >= 3:
                raise APIError("QUEUE_FULL", "最多同时保留 3 个待处理任务。", 429)
            if len(self.jobs) >= 1000:
                raise APIError("STORAGE_FULL", "已达到 1000 个任务，请停止服务后归档旧任务目录并重启。", 503)
            job_id = secrets.token_hex(16)
            self.jobs[job_id] = {"id": job_id, "state": "queued", "files": [], "created_at": int(time.time())}
            self.update(job_id)
            self.executor.submit(self.download, job_id, url)
            return self.job(job_id)

    def download(self, job_id, url):
        try:
            with self.upstream:
                if self.stop.is_set():
                    raise APIError("INTERRUPTED", "服务已停止。")
                self.update(job_id, state="parsing")
                result, session = self.parser(url, self.session, self.proxy)
                self.update(job_id, state="downloading", title=result["title"], author=result["author"],
                            source_url=result["source_url"], total=len(result["assets"]))
                # API and media use the same explicit network exit.
                opener = media_opener(session["cookies"], proxies={"http": self.proxy, "https": self.proxy} if self.proxy else {})
                files, failures = [], []
                for index, asset in enumerate(result["assets"], 1):
                    try:
                        file = download_asset(asset, self.output / job_id, index, opener, session["user_agent"],
                                              self.stop.is_set, lambda message: self.update(job_id, message=message))
                        file["download_url"] = f"/v1/downloads/{job_id}/files/{file['name']}"
                        files.append(file)
                        self.update(job_id, files=list(files))
                    except ExtractError as exc:
                        failures.append({"index": index, "message": str(exc)})
                state = "done" if not failures else "partial" if files else "error"
                extra = {"error": {"code": "DOWNLOAD_FAILED", "message": "所有媒体下载失败。"}} if not files else {}
                self.update(job_id, state=state, files=files, failures=failures,
                            message=f"已保存 {len(files)} / {len(result['assets'])} 个文件", **extra)
        except Exception as exc:
            code = exc.code if isinstance(exc, APIError) else "INTERNAL_ERROR"
            message = str(exc) if isinstance(exc, APIError) else "处理失败，请检查服务配置。"
            self.update(job_id, state="error", error={"code": code, "message": message})

    def close(self):
        self.stop.set()
        self.executor.shutdown(wait=True)


def create_app(*, session=None, output=None, key=None, proxy=None, parser=parse):
    runtime = Runtime(Path(session or os.getenv("DOUYIN_SESSION_FILE", ROOT / ".api-session.json")),
                      Path(output or os.getenv("DOUYIN_OUTPUT_DIR", ROOT / ".api-data")),
                      key or saved_key(ROOT / ".api-key"), proxy or os.getenv("DOUYIN_PROXY"), parser)

    @asynccontextmanager
    async def lifespan(app):
        yield
        runtime.close()

    app = FastAPI(title="抖音视频／图集解析 API", version="1.0.0", lifespan=lifespan,
                  description="直接请求抖音网页接口。首次使用请配置会话。支持解析、异步下载和文件获取。")
    app.state.runtime = runtime
    security = HTTPBearer(auto_error=False)

    def auth(credential: HTTPAuthorizationCredentials | None = Depends(security)):
        if not credential or not secrets.compare_digest(credential.credentials, runtime.key):
            raise APIError("UNAUTHORIZED", "请提供有效的 Bearer API 密钥。", 401)

    @app.middleware("http")
    async def no_store(request: Request, call_next):
        # Reject oversized streamed bodies too, including requests with no Content-Length.
        if request.method == "POST":
            body = bytearray()
            async for chunk in request.stream():
                body.extend(chunk)
                if len(body) > 16000:
                    return JSONResponse({"error": {"code": "REQUEST_TOO_LARGE", "message": "请求不能超过 16 KB。"}}, 413)
            request._body = bytes(body)
        response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @app.exception_handler(APIError)
    async def api_error(request, exc):
        return JSONResponse({"error": {"code": exc.code, "message": str(exc)}}, exc.status)

    @app.exception_handler(RequestValidationError)
    async def invalid_request(request, exc):
        return JSONResponse({"error": {"code": "INVALID_REQUEST", "message": "请求必须包含 1–8192 字符的 text 字段，且不含其他字段。"}}, 422)

    @app.get("/healthz")
    def health():
        return {"status": "ok"}

    @app.get("/v1/status", dependencies=[Depends(auth)])
    def status():
        try:
            load_session(runtime.session)
            configured = True
        except APIError:
            configured = False
        return {"session_configured": configured, "upstream_verified": False,
                "note": "已配置不代表会话仍有效，请调用解析接口验证。"}

    @app.post("/v1/parse", response_model=ParseResponse, dependencies=[Depends(auth)])
    def parse_post(body: ParseRequest):
        if not runtime.upstream.acquire(blocking=False):
            raise APIError("BUSY", "正在处理其他作品，请稍后重试。", 429)
        try:
            result, _ = runtime.parser(body.text, runtime.session, runtime.proxy)
            return ParseResponse(data=result)
        finally:
            runtime.upstream.release()

    @app.post("/v1/downloads", status_code=202, dependencies=[Depends(auth)])
    def download_post(body: ParseRequest):
        return {"data": runtime.enqueue(body.text)}

    @app.get("/v1/downloads/{job_id}", dependencies=[Depends(auth)])
    def download_status(job_id: str):
        return {"data": runtime.job(job_id)}

    @app.get("/v1/downloads/{job_id}/files/{name}", dependencies=[Depends(auth)])
    def download_file(job_id: str, name: str):
        job = runtime.job(job_id)
        if not any(file["name"] == name for file in job["files"]):
            raise APIError("NOT_FOUND", "文件不存在。", 404)
        path = (runtime.output / job_id / name).resolve()
        if not path.is_relative_to(runtime.output.resolve()) or not path.is_file():
            raise APIError("NOT_FOUND", "文件不存在。", 404)
        return FileResponse(path, filename=name)

    return app


if __name__ == "__main__":
    import uvicorn
    cli = argparse.ArgumentParser()
    cli.add_argument("--host", default="127.0.0.1")
    cli.add_argument("--port", default=8766, type=int)
    args = cli.parse_args()
    app = create_app()
    print(f"API 文档：http://{args.host}:{args.port}/docs\n密钥：DOUYIN_API_KEY 环境变量，或本地 {ROOT / '.api-key'}", flush=True)
    uvicorn.run(app, host=args.host, port=args.port, access_log=False)
