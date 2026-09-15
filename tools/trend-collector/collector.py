"""Small bridge from xhs-cli / MediaCrawler exports to the application's trend feeds.

Only public content fields are emitted. Login cookies and raw provider responses
are never served or logged. A failed collection leaves the last feed untouched.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone, timedelta
import html
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from urllib.parse import urlparse

PLATFORMS = {"xhs": "xiaohongshu", "dy": "douyin", "wb": "weibo"}
RULES = {
    "通勤": ("通勤", "西装", "office", "blazer"), "极简": ("极简", "简约", "minimal"),
    "丹宁": ("牛仔", "丹宁", "denim", "jeans"), "轻户外": ("户外", "机能", "gorpcore"),
    "运动休闲": ("运动", "sneaker"), "复古": ("复古", "vintage"),
    "针织": ("针织", "毛衣", "knit"), "裙装": ("裙", "dress", "skirt"),
    "层次叠穿": ("叠穿", "外套", "layer"), "街头": ("街头", "street"),
}


def text(value):
    return html.unescape(re.sub(r"<[^>]*>", "", str(value or ""))).strip()


def count(value):
    # Rounded '1.2万' values are not exact counters and cannot support growth.
    if value is None or isinstance(value, bool) or not str(value).isdigit():
        return None
    number = int(value)
    return number if 0 <= number <= 10**12 else None


def instant(value):
    if value is None or value == "":
        raise ValueError("publication/observation time missing")
    if isinstance(value, (int, float)) or str(value).isdigit():
        stamp = float(value)
        if stamp > 10**11:
            stamp /= 1000
        return datetime.fromtimestamp(stamp, timezone.utc)
    parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("timestamp timezone missing")
    return parsed.astimezone(timezone.utc)


def http_url(value):
    value = str(value or "")
    parsed = urlparse(value)
    return value if parsed.scheme in ("http", "https") and parsed.hostname and not parsed.username else None


def image_urls(raw):
    if isinstance(raw, str):
        raw = raw.split(",")
    result = []
    for item in raw or []:
        value = item.get("url_default") or item.get("url") if isinstance(item, dict) else item
        url = http_url(value)
        if url and url not in result:
            result.append(url)
    return result[:20]


def normalize(row, platform, observed=None):
    """Accept MediaCrawler content export fields (not comment/creator exports)."""
    item_id = row.get("aweme_id") if platform == "douyin" else row.get("note_id")
    if not item_id:
        raise ValueError("content ID missing")
    title = text(row.get("title") or row.get("content") or row.get("desc"))
    description = text(row.get("desc") or row.get("content"))
    combined = title + " " + description
    tags = [tag for tag, keywords in RULES.items() if any(k in combined.lower() for k in keywords)]
    if not tags and not any(k in combined.lower() for k in ("穿搭", "搭配", "outfit", "ootd", "fashion")):
        raise ValueError("not fashion content")
    published = instant(row.get("time") or row.get("create_time"))
    fetched = instant(row.get("last_modify_ts")) if row.get("last_modify_ts") else observed
    if fetched is None:
        raise ValueError("observation time missing; do not relabel old exports as fresh")
    now = datetime.now(timezone.utc)
    if published > now + timedelta(minutes=1) or fetched > now + timedelta(minutes=1):
        raise ValueError("future timestamp")
    images = image_urls(row.get("image_list") or row.get("note_download_url") or [])
    cover = http_url(row.get("cover_url")) or (images[0] if images else None)
    source = http_url(row.get("aweme_url") or row.get("note_url"))
    allowed = {"douyin": ("douyin.com",), "xiaohongshu": ("xiaohongshu.com",), "weibo": ("weibo.com", "weibo.cn")}[platform]
    host = urlparse(source or "").hostname or ""
    if not any(host == domain or host.endswith("." + domain) for domain in allowed):
        raise ValueError("source platform mismatch")
    return {
        "id": str(item_id)[:120], "platform": platform, "title": title[:200], "summary": description[:300],
        "topicTags": (tags or ["穿搭灵感"])[:10], "heatScore": 0,
        "publishedAt": published.isoformat(), "fetchedAt": fetched.isoformat(),
        "sourceUrl": source, "imageUrl": cover,
        "evidence": {"author": text(row.get("nickname"))[:120], "mediaType": "video" if row.get("video_download_url") else "image",
                     "images": images or ([cover] if cover else []), "likes": count(row.get("liked_count")),
                     "favorites": count(row.get("collected_count")), "comments": count(row.get("comment_count", row.get("comments_count"))),
                     "reposts": count(row.get("share_count", row.get("shared_count")))},
    }


def write_feed(rows, platform, output, observed=None):
    items = {}
    skipped = 0
    for row in rows:
        try:
            item = normalize(row, platform, observed)
            previous = items.get(item["id"])
            if previous is None or instant(item["fetchedAt"]) > instant(previous["fetchedAt"]):
                items[item["id"]] = item
        except (ValueError, TypeError, OverflowError, OSError):
            skipped += 1
    if not items:
        raise ValueError("No usable fashion content; previous feed preserved")
    ordered = sorted(items.values(), key=lambda i: instant(i["publishedAt"]), reverse=True)[:50]
    output.mkdir(parents=True, exist_ok=True)
    target = output / f"{platform}.json"
    temporary = target.with_suffix(".tmp")
    temporary.write_text(json.dumps({"items": ordered}, ensure_ascii=False), encoding="utf-8")
    temporary.replace(target)
    print(json.dumps({"platform": platform, "items": len(ordered), "skipped": skipped}))


def xhs_search(keyword, limit):
    if not (Path.home() / ".xiaohongshu-cli" / "cookies.json").exists():
        raise ValueError("XHS_SESSION_REQUIRED: run xhs login after signing in to Xiaohongshu")
    executable = shutil.which("xhs") or str(Path(sys.executable).parent / ("xhs.exe" if os.name == "nt" else "xhs"))
    def call(*args):
        result = subprocess.run([executable, *args, "--json"], capture_output=True, encoding="utf-8", timeout=90,
                                env={**os.environ, "PYTHONIOENCODING": "utf-8"})
        if result.returncode:
            raise ValueError("XHS_REQUEST_FAILED: check session in xhs; raw output not logged")
        data = json.loads(result.stdout)
        return data.get("data", data)
    search = call("search", keyword, "--sort", "popular", "--type", "image")
    rows = []
    for result in search.get("items", [])[:limit]:
        note_id = result.get("id")
        if not note_id or not result.get("note_card"):
            continue
        # Search has cached the source-bound security token; never construct a naked note URL for read.
        detail = call("read", note_id)
        notes = detail.get("items", [])
        for note in notes:
            card = note.get("note_card", {})
            interaction = card.get("interact_info", {})
            rows.append({"note_id": card.get("note_id") or note_id, "title": card.get("title"), "desc": card.get("desc"),
                         "time": card.get("time"), "nickname": card.get("user", {}).get("nickname"),
                         "image_list": card.get("image_list"), "note_url": f"https://www.xiaohongshu.com/explore/{note_id}",
                         **interaction})
        time.sleep(3)
    return rows


def serve(directory, port):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path not in [f"/{p}.json" for p in PLATFORMS.values()]:
                self.send_error(404); return
            path = directory / self.path[1:]
            if not path.is_file():
                self.send_error(503, "Source not collected"); return
            body = path.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers(); self.wfile.write(body)
        def log_message(self, *args):
            pass
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["import", "xhs", "serve"])
    parser.add_argument("--platform", choices=list(PLATFORMS.values()), default="xiaohongshu")
    parser.add_argument("--input", type=Path, help="MediaCrawler content JSON export")
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "data")
    parser.add_argument("--keyword", default="通勤穿搭")
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--interval", type=int, default=0, help="Repeat xhs collection in seconds (minimum 21600)")
    parser.add_argument("--port", type=int, default=8767)
    args = parser.parse_args()
    if not 1 <= args.limit <= 20: parser.error("limit must be 1..20")
    if args.interval and args.interval < 21600: parser.error("interval must be at least 21600 seconds")
    if args.mode == "serve": serve(args.output, args.port); return
    if args.mode == "import":
        if not args.input: parser.error("--input is required")
        data = json.loads(args.input.read_text(encoding="utf-8-sig"))
        write_feed(data if isinstance(data, list) else data["items"], args.platform, args.output)
        return
    while True:
        try: write_feed(xhs_search(args.keyword, args.limit), "xiaohongshu", args.output, datetime.now(timezone.utc))
        except (ValueError, subprocess.TimeoutExpired, OSError) as error:
            print(str(error), file=sys.stderr)
            if not args.interval: raise SystemExit(1) from None
        if not args.interval: break
        time.sleep(args.interval)


if __name__ == "__main__":
    main()
