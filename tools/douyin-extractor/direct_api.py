"""Direct HTTP extraction. Browser is used only by setup_session.py."""
from __future__ import annotations

import json
import re
import time
from pathlib import Path
from urllib.parse import parse_qsl, quote, urlencode, urljoin, urlparse

from curl_cffi import requests

from extractor import PAGE_HOSTS, find_work, share_url, work_id
from vendor.dtk_signing.abogus import ABogus, browser_info_from_screen
from vendor.dtk_signing import websign

DETAIL_URL = "https://www.douyin.com/aweme/v1/web/aweme/detail/"


class APIError(Exception):
    def __init__(self, code, message, status=502):
        super().__init__(message)
        self.code, self.status = code, status


def load_session(path: Path) -> dict:
    try:
        data = json.loads(path.read_text())
        cookies = data["cookies"]
        if not isinstance(cookies, list) or not data["user_agent"]:
            raise ValueError()
        jar = {c["name"]: c["value"] for c in cookies
               if c.get("expires", -1) <= 0 or c["expires"] > time.time()}
        if not websign.pick_uifid(jar):
            raise ValueError()
        return data
    except (OSError, ValueError, KeyError, TypeError):
        raise APIError("SESSION_REQUIRED", "请先运行 setup_session.py 配置或更新抖音会话。", 503) from None


def build_query(item_id: str, session: dict) -> tuple[str, dict]:
    ua = session["user_agent"]
    match = re.search(r"Chrome/([\d.]+)", ua)
    if not match:
        raise APIError("SESSION_REQUIRED", "请使用 setup_session.py 重新生成 Chromium 会话。", 503)
    version = match[1]
    platform = session.get("platform", "MacIntel")
    width, height = session.get("screen_width", 1920), session.get("screen_height", 1080)
    os_name = "Windows" if "Windows" in ua else "Mac OS" if "Macintosh" in ua else "Linux"
    os_match = re.search(r"Mac OS X ([\d_]+)", ua)
    os_version = "10" if os_name == "Windows" else os_match[1].replace("_", ".") if os_match else ""
    params = {
        "device_platform": "webapp", "aid": "6383", "channel": "channel_pc_web",
        "pc_client_type": "1", "version_code": "290100", "version_name": "29.1.0",
        "cookie_enabled": "true", "screen_width": str(width), "screen_height": str(height),
        "browser_language": session.get("language", "zh-CN"), "browser_platform": platform,
        "browser_name": "Chrome", "browser_version": version, "browser_online": "true",
        "engine_name": "Blink", "engine_version": version, "os_name": os_name,
        "os_version": os_version, "cpu_core_num": str(session.get("cpu_core_num", 8)),
        "device_memory": str(session.get("device_memory", 8)), "platform": "PC",
        "downlink": "10", "effective_type": "4g", "round_trip_time": "0",
        "update_version_code": "170400", "aweme_id": item_id,
    }
    jar = {c["name"]: c["value"] for c in session["cookies"]
           if c.get("expires", -1) <= 0 or c["expires"] > time.time()}
    if jar.get("msToken"):
        params["msToken"] = jar["msToken"]
    query = urlencode(params)
    signature = ABogus(ua, browser_info=browser_info_from_screen(width, height, platform)).get_value(query)
    pairs = parse_qsl(query + "&a_bogus=" + quote(signature, safe=""), keep_blank_values=True)
    if jar.get("s_v_web_id"):
        pairs.extend([(name, jar["s_v_web_id"]) for name in ("verifyFp", "fp")])
    uifid = websign.pick_uifid(jar)
    if not uifid:
        raise APIError("SESSION_REQUIRED", "会话缺少访客参数，请重新运行 setup_session.py。", 503)
    query, _, headers = websign.sign(pairs, uifid)
    return query, headers


def resolve_id(client, text: str) -> str:
    from extractor import ExtractError
    try:
        url = share_url(text)
    except ExtractError as exc:
        raise APIError("INVALID_URL", str(exc), 400) from None
    for _ in range(6):
        if item_id := work_id(url):
            return item_id
        p = urlparse(url)
        if p.scheme != "https" or p.hostname not in PAGE_HOSTS or p.username or p.password or p.port not in (None, 443):
            raise APIError("INVALID_REDIRECT", "分享链接跳转到了不支持的地址。", 400)
        response = client.get(url, allow_redirects=False, timeout=8)
        if response.status_code not in (301, 302, 303, 307, 308):
            raise APIError("LINK_UNRESOLVED", "短链接未返回作品地址，请尝试作品的完整链接。")
        url = urljoin(url, response.headers.get("location", ""))
        # Validate before returning even when the destination already contains an id.
        p = urlparse(url)
        if p.scheme != "https" or p.hostname not in PAGE_HOSTS or p.username or p.password or p.port not in (None, 443):
            raise APIError("INVALID_REDIRECT", "分享链接跳转到了不支持的地址。", 400)
    raise APIError("LINK_UNRESOLVED", "分享链接跳转次数过多。")


def parse(text: str, session_path: Path, proxy: str | None = None) -> tuple[dict, dict]:
    from extractor import ExtractError
    try:
        text = share_url(text)
    except ExtractError as exc:
        raise APIError("INVALID_URL", str(exc), 400) from None
    session = load_session(session_path)
    try:
        # Automatic redirects are disabled: a share URL must never become a general URL fetcher.
        with requests.Session(impersonate="chrome", trust_env=False, proxy=proxy,
                              headers={"User-Agent": session["user_agent"], "Referer": "https://www.douyin.com/"}) as client:
            for cookie in session["cookies"]:
                domain = cookie.get("domain", "").lstrip(".")
                if (domain == "douyin.com" or domain.endswith(".douyin.com")) and (
                    cookie.get("expires", -1) <= 0 or cookie["expires"] > time.time()
                ):
                    client.cookies.set(cookie["name"], cookie["value"], domain=cookie["domain"], path=cookie.get("path", "/"))
            item_id = resolve_id(client, text)
            query, headers = build_query(item_id, session)
            response = client.get(DETAIL_URL + "?" + query, headers=headers, allow_redirects=False, timeout=20)
            if response.status_code in (401, 403, 429) or not response.content:
                raise APIError("UPSTREAM_REJECTED", "抖音拒绝了请求或返回空内容，请更新会话并稍后重试。")
            if response.status_code != 200:
                raise APIError("UPSTREAM_HTTP_ERROR", f"抖音接口返回 HTTP {response.status_code}。")
            try:
                data = response.json()
            except ValueError:
                raise APIError("UPSTREAM_REJECTED", "抖音返回了验证页面，请在会话配置浏览器中处理。") from None
            result = find_work(data, item_id)
            if not result:
                raise APIError("MEDIA_UNAVAILABLE", "接口未返回完整媒体数据；作品可能不可见，或会话／接口已变化。")
            result.update(source_url=f"https://www.douyin.com/{'note' if result['type'] == '图集' else 'video'}/{item_id}",
                          provider="douyin_web_api", parsed_at=int(time.time()))
            return result, session
    except APIError:
        raise
    except requests.errors.RequestsError:
        raise APIError("UPSTREAM_CONNECTION", "连接抖音失败或超时，请检查网络及 DOUYIN_PROXY。") from None
