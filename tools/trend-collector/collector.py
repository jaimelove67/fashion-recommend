"""Small bridge from MediaCrawler exports to the application's trend feeds.

Only public content fields are emitted. Login cookies and raw provider responses
are never served or logged. A failed collection leaves the last feed untouched.

Modes:
  import  normalize a MediaCrawler-style content JSON export into data/<platform>.json
  status  inventory data/ against the backend contract without contacting the backend
  serve   expose data/<platform>.json over HTTP for TREND_DOUYIN_URL and friends
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone, timedelta
import html
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
from urllib.parse import urlparse

PLATFORMS = {"dy": "douyin", "wb": "weibo"}
# The backend rejects an import file larger than this, so status reports readiness the same way.
MAX_FEED_BYTES = 2_000_000
TREND_WINDOW_HOURS = 7 * 24
RULES = {
    "通勤": ("通勤", "西装", "office", "blazer"), "极简": ("极简", "简约", "minimal"),
    "丹宁": ("牛仔", "丹宁", "denim", "jeans"), "轻户外": ("户外", "机能", "gorpcore"),
    "运动休闲": ("运动", "sneaker"), "复古": ("复古", "vintage"),
    "针织": ("针织", "毛衣", "knit"), "裙装": ("裙", "dress", "skirt"),
    "层次叠穿": ("叠穿", "外套", "layer"), "街头": ("街头", "street"),
}
SHOW_MARKERS = ("红毯", "red carpet", "red-carpet", "时装周", "fashion week", "fashion-week",
                "runway", "runway show", "catwalk", "秀场", "走秀", "大秀")
CELEBRITY_MARKERS = ("明星", "艺人", "演员", "歌手", "女星", "男星", "影后", "影帝", "偶像", "名人")
CELEBRITY_STYLE_MARKERS = ("造型", "穿搭", "搭配", "礼服", "首映", "典礼", "封面", "大片", "珠宝", "亮相", "出席")
EDITORIAL_STYLE_MARKERS = ("红毯造型", "明星造型", "明星穿搭", "女星造型", "男星造型", "时装大片", "封面造型",
                           "杂志", "画报", "时尚大片", "封面拍摄", "magazine", "fashion editorial", "editorial shoot")


def text(value):
    return html.unescape(re.sub(r"<[^>]*>", "", str(value or ""))).strip()


def count(value):
    # Rounded '1.2万' values are not exact counters and cannot support growth.
    if value is None or isinstance(value, bool) or not str(value).isdigit():
        return None
    number = int(value)
    return number if 0 <= number <= 10**12 else None


def count_label(value):
    """Keep rounded follower labels for display and conservative tier classification."""
    if count(value) is not None or value is None:
        return None
    label = str(value).strip()
    return label if re.fullmatch(r"\d+(?:\.\d+)?\s*(?:万|亿|[wW]|千|[kK])", label) else None


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


def utc_now(value=None):
    """Return an aware UTC instant, allowing deterministic import/status checks."""
    current = datetime.now(timezone.utc) if value is None else value
    if current.tzinfo is None:
        raise ValueError("now timezone missing")
    return current.astimezone(timezone.utc)


def is_within_window(value, now, hours=TREND_WINDOW_HOURS):
    """Match TrendService's inclusive recent-publication window."""
    published = instant(value)
    current = utc_now(now)
    return current - timedelta(hours=hours) <= published <= current


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


def excluded_show_or_celebrity(value):
    """Keep creator outfit sharing out of red-carpet, runway and celebrity-editorial feeds."""
    lower = text(value).lower()
    if any(marker in lower for marker in SHOW_MARKERS + EDITORIAL_STYLE_MARKERS):
        return True
    return any(marker in lower for marker in CELEBRITY_MARKERS) and any(
        marker in lower for marker in CELEBRITY_STYLE_MARKERS)


def normalize(row, platform, observed=None, now=None):
    """Accept MediaCrawler content export fields (not comment/creator exports)."""
    if platform not in PLATFORMS.values():
        raise ValueError("unsupported platform")
    item_id = row.get("aweme_id") if platform == "douyin" else row.get("note_id")
    if not item_id:
        raise ValueError("content ID missing")
    title = text(row.get("title") or row.get("content") or row.get("desc"))
    description = text(row.get("desc") or row.get("content"))
    raw_tags = row.get("topicTags") or row.get("tags") or ""
    combined = title + " " + description + " " + text(raw_tags)
    if excluded_show_or_celebrity(combined):
        raise ValueError("outside creator outfit trend scope")
    tags = [tag for tag, keywords in RULES.items() if any(k in combined.lower() for k in keywords)]
    if not tags and not any(k in combined.lower() for k in ("穿搭", "搭配", "outfit", "ootd", "fashion")):
        raise ValueError("not fashion content")
    published = instant(row.get("time") or row.get("create_time"))
    fetched = instant(row.get("last_modify_ts")) if row.get("last_modify_ts") else observed
    if fetched is None:
        raise ValueError("observation time missing; do not relabel old exports as fresh")
    current = utc_now(now)
    if published > current + timedelta(minutes=1) or fetched > current + timedelta(minutes=1):
        raise ValueError("future timestamp")
    images = image_urls(row.get("image_list") or row.get("note_download_url") or [])
    cover = http_url(row.get("cover_url")) or (images[0] if images else None)
    reviewed_image = http_url(row.get("full_body_image_url"))
    full_body_image = reviewed_image if row.get("full_body_image_verified") is True and reviewed_image in (images + [cover]) else None
    source = http_url(row.get("aweme_url") or row.get("note_url"))
    follower_count = next((row.get(field) for field in (
        "authorFollowers", "author_followers_count", "author_follower_count", "author_followers",
        "user_followers_count", "user_follower_count", "followerCount", "followers_count", "follower_count",
        "followers", "author_fans_count", "fans_count", "fans",
    ) if row.get(field) not in (None, "")), None)
    video_flag = str(row.get("is_video") or "").strip().lower() in ("1", "true", "yes")
    media_type = str(row.get("media_type") or row.get("type") or "").lower()
    is_video = platform == "douyin" or any(row.get(field) for field in (
        "video_download_url", "video_url", "video_play_url", "video_info", "video_list", "video_urls",
    )) or video_flag or media_type.startswith("video") or media_type in ("short_video", "2")
    allowed = {"douyin": ("douyin.com",), "weibo": ("weibo.com", "weibo.cn", "sina.cn")}[platform]
    host = urlparse(source or "").hostname or ""
    if not any(host == domain or host.endswith("." + domain) for domain in allowed):
        raise ValueError("source platform mismatch")
    return {
        "id": str(item_id)[:120], "platform": platform, "title": title[:200], "summary": description[:300],
        "topicTags": (tags or ["穿搭灵感"])[:10], "heatScore": 0,
        "publishedAt": published.isoformat(), "fetchedAt": fetched.isoformat(),
        "sourceUrl": source, "imageUrl": cover,
        "evidence": {"author": text(row.get("nickname"))[:120], "mediaType": "video" if is_video else "image",
                     "images": images or ([cover] if cover else []), "likes": count(row.get("liked_count")),
                     "favorites": count(row.get("collected_count")), "comments": count(row.get("comment_count", row.get("comments_count"))),
                     "reposts": count(row.get("share_count", row.get("shared_count"))),
                     "authorFollowers": count(follower_count),
                     "authorFollowersLabel": count_label(follower_count),
                     "fullBodyImageUrl": full_body_image},
    }


def write_feed(rows, platform, output, observed=None, require_fresh_hours=None, now=None):
    current = utc_now(now)
    if require_fresh_hours is not None and require_fresh_hours < 0:
        raise ValueError("require_fresh_hours must be non-negative")
    items = {}
    skipped = 0
    for row in rows:
        try:
            item = normalize(row, platform, observed, current)
            previous = items.get(item["id"])
            if previous is None or instant(item["fetchedAt"]) > instant(previous["fetchedAt"]):
                items[item["id"]] = item
        except (ValueError, TypeError, OverflowError, OSError):
            skipped += 1
    if not items:
        raise ValueError("No usable fashion content; previous feed preserved")
    ordered = sorted(items.values(), key=lambda i: instant(i["publishedAt"]), reverse=True)[:50]
    fresh_items = [item for item in ordered if is_within_window(item["publishedAt"], current,
                                                                require_fresh_hours if require_fresh_hours is not None else TREND_WINDOW_HOURS)]
    if require_fresh_hours is not None and not fresh_items:
        raise ValueError(f"No content published in the last {require_fresh_hours:g} hours; previous feed preserved")
    output.mkdir(parents=True, exist_ok=True)
    target = output / f"{platform}.json"
    temporary = target.with_suffix(".tmp")
    temporary.write_text(json.dumps({"items": ordered}, ensure_ascii=False), encoding="utf-8")
    temporary.replace(target)
    print(json.dumps({"platform": platform, "items": len(ordered), "skipped": skipped,
                      "newestPublishedAt": ordered[0]["publishedAt"], "freshItems": len(fresh_items)},
                     ensure_ascii=False))


def report(output, now=None):
    """Inventory the feed directory the backend reads. Never contacts the backend."""
    current = utc_now(now)
    sources = []
    for platform in sorted(set(PLATFORMS.values())):
        path = output / f"{platform}.json"
        entry = {"platform": platform, "file": str(path)}
        if not path.is_file():
            sources.append({**entry, "state": "missing", "items": 0, "newestPublishedAt": None,
                            "eligibleItems": 0, "writtenAt": None, "withinBackendLimit": None,
                            "note": "尚未导入；趋势页会把该来源显示为未接通"})
            continue
        stats = path.stat()
        written = datetime.fromtimestamp(stats.st_mtime, timezone.utc).isoformat()
        within = stats.st_size <= MAX_FEED_BYTES
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            items = data.get("items") if isinstance(data, dict) else data
            if not isinstance(items, list):
                raise ValueError("items must be a list")
        except (ValueError, OSError):
            sources.append({**entry, "state": "unreadable", "items": 0, "newestPublishedAt": None,
                            "eligibleItems": 0, "writtenAt": written, "withinBackendLimit": within,
                            "note": "不是后端契约所需的 {\"items\": [...]} JSON；请重新导入"})
            continue
        if not within:
            state, note = "oversized", f"超过后端 {MAX_FEED_BYTES} 字节上限，后端会拒绝该文件"
        elif not items:
            state, note = "empty", "文件存在但没有可用条目"
        else:
            state, note = "ready", "可被后端读取；刷新趋势来源后即可展示"
        published = []
        for item in items:
            if not isinstance(item, dict) or not item.get("publishedAt"):
                continue
            try:
                published.append((instant(item["publishedAt"]), str(item["publishedAt"])))
            except (ValueError, TypeError, OverflowError):
                continue
        newest = max(published, default=(None, None), key=lambda pair: pair[0])
        eligible = [item for item in published if current - timedelta(hours=TREND_WINDOW_HOURS)
                    <= item[0] <= current]
        if within and items and not eligible:
            state = "stale"
            if newest[0] is None:
                note = "没有可解析的发布时间；无法确认条目属于最近 7 天，趋势页不会将其作为最近趋势"
            else:
                note = (f"最新内容发布于 {newest[1]}，早于最近 7 天；不会进入最近 7 天趋势"
                        "（若后端已验证互动增长，后端可按互动增长规则另行判断）")
        elif within and items and len(eligible) < len(items):
            note = (f"有 {len(eligible)} 条内容发布于最近 7 天，可进入趋势；其余旧内容仅在后端"
                    "验证互动增长时保留")
        sources.append({**entry, "state": state, "items": len(items),
                        "eligibleItems": len(eligible),
                        "newestPublishedAt": newest[1],
                        "writtenAt": written, "withinBackendLimit": within, "note": note})
    print(json.dumps({"directory": str(output), "backendLimitBytes": MAX_FEED_BYTES, "sources": sources},
                     ensure_ascii=False, indent=2))
    return sources


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
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mode", choices=["import", "status", "serve"])
    parser.add_argument("--platform", choices=list(PLATFORMS.values()), default="douyin")
    parser.add_argument("--input", type=Path, nargs="+", help="MediaCrawler content JSON export (one or more files)")
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "data")
    parser.add_argument("--port", type=int, default=8767)
    parser.add_argument("--require-fresh-hours", type=float,
                        help="import only if at least one item was published within this many hours")
    args = parser.parse_args()
    if args.mode == "status": report(args.output); return
    if args.mode == "serve": serve(args.output, args.port); return
    if args.mode == "import":
        if not args.input: parser.error("--input is required")
        rows = []
        for path in args.input:
            if not path.is_file(): parser.error(f"input file not found: {path}")
            data = json.loads(path.read_text(encoding="utf-8-sig"))
            rows.extend(data if isinstance(data, list) else data["items"])
        write_feed(rows, args.platform, args.output, require_fresh_hours=args.require_fresh_hours)
        report(args.output)
        return


if __name__ == "__main__":
    main()
