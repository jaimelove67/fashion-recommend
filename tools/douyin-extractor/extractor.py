"""Douyin page-data extractor. No private API signing or third-party parsing service."""
from __future__ import annotations

import asyncio
import http.cookiejar
import ipaddress
import json
import re
import socket
import time
import urllib.request
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse


class ExtractError(Exception):
    pass


PAGE_HOSTS = {"douyin.com", "www.douyin.com", "v.douyin.com", "www.iesdouyin.com", "iesdouyin.com"}
MEDIA_DOMAINS = ("douyin.com", "iesdouyin.com", "douyincdn.com", "douyinvod.com",
                 "douyinpic.com", "idouyinvod.com", "byteimg.com", "ibyteimg.com",
                 "pstatp.com", "snssdk.com", "bytecdn.cn", "bytecdn.com", "ixigua.com")
MAX_FILE = 512 * 1024 * 1024


def share_url(text: str) -> str:
    for match in re.finditer(r"https?://[^\s<>\"'，。！？；（）【】]+", text):
        url = match.group().rstrip(".,!;:?)】)")
        try:
            p = urlparse(url)
            if p.hostname in PAGE_HOSTS and not p.username and not p.password and p.port in (None, 80, 443):
                if p.scheme == "http":
                    url = "https:" + url[5:]
                if p.hostname == "v.douyin.com" or work_id(url):
                    return url
        except ValueError:
            continue
    raise ExtractError("请粘贴抖音作品分享文本或视频／图文链接，不支持主页、直播和其他网站。")


def work_id(url: str) -> str | None:
    p = urlparse(url)
    match = re.search(r"/(?:video|note|slides|share/video|share/slides)/(\d+)", p.path)
    if match:
        return match.group(1)
    return next((v[0] for k, v in parse_qs(p.query).items()
                 if k in ("modal_id", "aweme_id") and v and v[0].isdigit()), None)


def urls(value) -> list[str]:
    if isinstance(value, str):
        return [value] if value.startswith(("https://", "http://")) else []
    if isinstance(value, list):
        return list(dict.fromkeys(u for v in value for u in urls(v)))
    if isinstance(value, dict):
        return urls(value.get("url_list") or value.get("urlList") or value.get("url") or [])
    return []


def normalize(item: dict, expected: str) -> dict | None:
    if str(item.get("aweme_id") or item.get("awemeId") or "") != expected:
        return None
    images = item.get("images") or (item.get("image_post_info") or {}).get("images") or []
    assets = []
    for image in images:
        candidates = urls(image)
        if not candidates and isinstance(image, dict):
            candidates = urls(image.get("display_image") or image.get("displayImage"))
        if candidates:
            assets.append({"kind": "image", "urls": candidates})
    if images and len(assets) != len(images):
        return None  # Never silently return an incomplete gallery or its soundtrack.
    if not images:
        video = item.get("video") or {}
        candidates = []
        rates = video.get("bit_rate") or video.get("bitRate") or []
        for rate in sorted(rates, key=lambda r: float(r.get("bit_rate") or r.get("bitRate") or 0), reverse=True):
            candidates.extend(urls(rate.get("play_addr") or rate.get("playAddr")))
        candidates.extend(urls(video.get("play_addr") or video.get("playAddr")))
        candidates.extend(urls(video.get("download_addr") or video.get("downloadAddr")))
        if candidates:
            assets.append({"kind": "video", "urls": list(dict.fromkeys(candidates))})
    if not assets:
        return None
    author = item.get("author") or {}
    return {"id": expected, "title": str(item.get("desc") or item.get("description") or expected),
            "author": str(author.get("nickname") or ""),
            "type": "图集" if images else "视频", "assets": assets}


def find_work(data, expected: str) -> dict | None:
    stack = [data]
    for _ in range(50000):
        if not stack:
            return None
        item = stack.pop()
        if isinstance(item, dict):
            try:
                result = normalize(item, expected)
            except (TypeError, ValueError, AttributeError):
                result = None
            if result:
                return result
            stack.extend(item.values())
        elif isinstance(item, list):
            stack.extend(item)
    return None


def checked_media_url(url: str) -> str:
    p = urlparse(url)
    host = p.hostname or ""
    if (p.scheme not in ("http", "https") or p.username or p.password or p.port not in (None, 80, 443)
            or not any(host == d or host.endswith("." + d) for d in MEDIA_DOMAINS)):
        raise ExtractError("媒体地址不在支持的抖音 CDN 域名范围内。")
    addresses = socket.getaddrinfo(host, p.port or (443 if p.scheme == "https" else 80), type=socket.SOCK_STREAM)
    if not addresses or any(not ipaddress.ip_address(a[4][0]).is_global for a in addresses):
        raise ExtractError("媒体地址解析到了非公网地址。")
    return url


class MediaRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        checked_media_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def media_opener(cookies: list[dict]):
    jar = http.cookiejar.CookieJar()
    for c in cookies:
        jar.set_cookie(http.cookiejar.Cookie(
            version=0, name=c["name"], value=c["value"], port=None, port_specified=False,
            domain=c["domain"], domain_specified=True, domain_initial_dot=c["domain"].startswith("."),
            path=c.get("path", "/"), path_specified=True, secure=c.get("secure", False),
            expires=int(c["expires"]) if c.get("expires", -1) > 0 else None,
            discard=True, comment=None, comment_url=None, rest={}))
    return urllib.request.build_opener(MediaRedirect(), urllib.request.HTTPCookieProcessor(jar))


def media_extension(head: bytes, kind: str) -> str:
    if kind == "video":
        if len(head) >= 12 and head[4:8] == b"ftyp":
            return ".mp4"
        if head.startswith(b"\x1a\x45\xdf\xa3"):
            return ".webm"
    else:
        if head.startswith(b"\xff\xd8\xff"):
            return ".jpg"
        if head.startswith(b"\x89PNG\r\n\x1a\n"):
            return ".png"
        if head[:4] == b"RIFF" and head[8:12] == b"WEBP":
            return ".webp"
        if head.startswith((b"GIF87a", b"GIF89a")):
            return ".gif"
        if head[4:8] == b"ftyp" and head[8:12] in (b"avif", b"avis", b"heic", b"heix", b"mif1"):
            return ".avif" if head[8:12] in (b"avif", b"avis") else ".heic"
    raise ExtractError("服务器未返回有效媒体文件，可能是验证页或链接已失效。")


def download_asset(asset, folder: Path, index: int, opener, ua: str, cancelled, progress):
    last_error = "没有可用的下载地址"
    part = folder / f"{index:03d}.part"
    for url in asset["urls"][:6]:
        if cancelled():
            raise ExtractError("任务已取消")
        try:
            checked_media_url(url)
            request = urllib.request.Request(url, headers={"User-Agent": ua, "Referer": "https://www.douyin.com/",
                                                          "Accept-Encoding": "identity"})
            with opener.open(request, timeout=20) as response:
                if response.status != 200:
                    raise ExtractError(f"下载服务器返回 HTTP {response.status}")
                size = int(response.headers.get("Content-Length") or 0)
                if size > MAX_FILE:
                    raise ExtractError("单文件超过 512 MB 限制")
                received, start, notified = 0, time.monotonic(), 0
                with part.open("wb") as output:
                    head = response.read(64)
                    ext = media_extension(head, asset["kind"])
                    output.write(head)
                    received += len(head)
                    while True:
                        if cancelled():
                            raise ExtractError("任务已取消")
                        if time.monotonic() - start > 300:
                            raise ExtractError("单文件下载超过 5 分钟")
                        chunk = response.read(128 * 1024)
                        if not chunk:
                            break
                        received += len(chunk)
                        if received > MAX_FILE:
                            raise ExtractError("单文件超过 512 MB 限制")
                        output.write(chunk)
                        if time.monotonic() - notified > .5:
                            progress(f"正在下载第 {index} 个文件 · {received / 1048576:.1f} MB")
                            notified = time.monotonic()
                if not received or (size and received != size):
                    raise ExtractError("文件下载不完整")
                path = folder / f"{index:03d}{ext}"
                part.replace(path)
                return {"name": path.name, "size": received, "kind": asset["kind"]}
        except Exception as exc:
            part.unlink(missing_ok=True)
            # Do not leak signed media URLs or cookies into the UI/logs.
            last_error = str(exc) if isinstance(exc, ExtractError) else f"下载连接失败（{type(exc).__name__}）"
    raise ExtractError(last_error)


async def extract_and_download(url: str, profile: Path, output: Path, update, cancelled,
                               *, headless=False, timeout=150):
    from playwright.async_api import async_playwright

    profile.mkdir(parents=True, exist_ok=True, mode=0o700)
    async with async_playwright() as playwright:
        update("opening", "正在打开独立浏览器…")
        launch_options = {}
        # Match the machine's configured HTTP proxy (including macOS system proxy).
        # Chromium's automated profile does not consistently inherit these settings.
        proxy_url = urllib.request.getproxies().get("https") or urllib.request.getproxies().get("http")
        if proxy_url:
            proxy = urlparse(proxy_url)
            if proxy.scheme in ("http", "https", "socks5") and proxy.hostname:
                host = f"[{proxy.hostname}]" if ":" in proxy.hostname else proxy.hostname
                config = {"server": f"{proxy.scheme}://{host}" + (f":{proxy.port}" if proxy.port else "")}
                if proxy.username:
                    config["username"] = unquote(proxy.username)
                    config["password"] = unquote(proxy.password or "")
                launch_options["proxy"] = config
        try:
            context = await playwright.chromium.launch_persistent_context(
                str(profile), channel="chromium", headless=headless, viewport={"width": 1200, "height": 850}, locale="zh-CN", **launch_options)
        except Exception as exc:
            raise ExtractError("浏览器启动失败。请关闭上次的程序浏览器；首次使用请运行：python -m playwright install chromium") from exc
        try:
            page = context.pages[0] if context.pages else await context.new_page()
            captured, tasks = [], set()

            async def collect(response):
                try:
                    p = urlparse(response.url)
                    if (p.hostname == "douyin.com" or (p.hostname or "").endswith(".douyin.com")) and "aweme" in p.path and "json" in response.headers.get("content-type", ""):
                        if int(response.headers.get("content-length", 0)) < 8 * 1024 * 1024:
                            data = await response.json()
                            captured.append(data)
                            del captured[:-12]
                except Exception:
                    pass

            def on_response(response):
                task = asyncio.create_task(collect(response))
                tasks.add(task)
                task.add_done_callback(tasks.discard)

            page.on("response", on_response)
            update("extracting", "正在读取作品；如浏览器显示登录或验证，请在浏览器内完成。")
            try:
                await page.goto(url, wait_until="domcontentloaded", timeout=30000)
            except Exception as exc:
                if page.is_closed():
                    raise ExtractError("浏览器已关闭，请重新提取。")
                error_code = re.search(r"net::(ERR_[A-Z_]+)", str(exc))
                if error_code:
                    raise ExtractError(f"浏览器无法打开作品（{error_code.group(1)}），请检查网络或系统代理后重试。") from exc
            deadline = time.monotonic() + timeout
            expected, result = work_id(url), None
            while time.monotonic() < deadline:
                if cancelled():
                    raise ExtractError("任务已取消")
                if page.is_closed():
                    raise ExtractError("浏览器已关闭，请重新提取。")
                current_host = urlparse(page.url).hostname
                if current_host not in PAGE_HOSTS:
                    await asyncio.sleep(1)
                    continue
                expected = expected or work_id(page.url)
                if expected:
                    for data in reversed(captured):
                        result = find_work(data, expected)
                        if result:
                            break
                    if not result:
                        try:
                            states = await page.evaluate("""() => {
                                const values = [];
                                for (const key of ['_ROUTER_DATA', '__INITIAL_STATE__', '__NEXT_DATA__']) {
                                    if (window[key]) { try {values.push(JSON.stringify(window[key]))} catch {}}
                                }
                                for (const s of document.querySelectorAll('script#RENDER_DATA,script#__NEXT_DATA__,script[type="application/json"]')) {
                                    if (s.textContent.length < 8000000) values.push(s.textContent);
                                }
                                return values;
                            }""")
                            for raw in states:
                                try:
                                    result = find_work(json.loads(unquote(raw) if raw.startswith("%") else raw), expected)
                                except (ValueError, TypeError):
                                    continue
                                if result:
                                    break
                        except Exception:
                            pass
                if result:
                    break
                await asyncio.sleep(1)
            for task in list(tasks):
                task.cancel()
            if tasks:
                await asyncio.gather(*tasks, return_exceptions=True)
            if not result:
                raise ExtractError("未读取到该作品的视频／图集数据。请确认作品能在浏览器中正常打开，完成登录或验证后重试；也可能是作品已删除、权限受限或抖音页面结构变化。")
            result["source_url"] = f"https://www.douyin.com/{'note' if result['type'] == '图集' else 'video'}/{result['id']}"
            result["extracted_at"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
            output.mkdir(parents=True, exist_ok=True)
            opener = media_opener(await context.cookies())
            ua = await page.evaluate("navigator.userAgent")
            files, failures = [], []
            update("downloading", f"已识别{result['type']}，共 {len(result['assets'])} 个文件", title=result["title"], total=len(result["assets"]))
            for i, asset in enumerate(result["assets"], 1):
                if cancelled():
                    raise ExtractError("任务已取消")
                try:
                    file = await asyncio.to_thread(download_asset, asset, output, i, opener, ua, cancelled,
                                                   lambda msg: update("downloading", msg))
                    files.append(file)
                    update("downloading", f"已保存 {len(files)} / {len(result['assets'])} 个文件", files=list(files))
                except ExtractError as exc:
                    if cancelled():
                        raise
                    failures.append({"index": i, "message": str(exc)})
            result["files"], result["failures"] = files, failures
            # Signed CDN addresses deliberately stay in memory only.
            result.pop("assets")
            (output / "metadata.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
            if not files:
                raise ExtractError(f"已识别作品，但全部文件下载失败：{failures[0]['message']}")
            return result
        finally:
            await context.close()
