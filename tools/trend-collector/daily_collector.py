"""Scrapling public searches, called by the backend's daily noon refresh.
Never reuse browser profiles, solve challenges, or invent dates/counters.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import logging
from pathlib import Path
import re
from urllib.parse import quote, unquote, urlparse
from collect_public import clean, eligible, feed_item, image_urls, publication, initial_state

UTC = timezone.utc
CST = timezone(timedelta(hours=8))
PLATFORMS = {'weibo', 'douyin', 'xiaohongshu'}
FASHION = re.compile(r'穿搭|搭配|OOTD|衬衫|牛仔|针织|裙|外套', re.I)


def recent(item, now):
    reason = eligible(item, now)
    if reason:
        return reason
    if publication(item['publishedAt']) < now - timedelta(hours=24):
        return 'outside-24-hour-window'
    return None


def weibo_date(raw, now):
    raw = clean(raw)
    if re.fullmatch(r'今天 \d{1,2}:\d{2}', raw):
        raw = now.astimezone(CST).strftime('%Y-%m-%d') + raw[2:]
    if re.fullmatch(r'\d{2}月\d{2}日 \d{1,2}:\d{2}', raw):
        raw = str(now.astimezone(CST).year) + '-' + raw.replace('月', '-').replace('日', '')
    try:
        return publication(raw).isoformat()
    except (ValueError, TypeError, OverflowError):
        return None


def parse_weibo(page, now):
    records = []
    for card in page.css('.card-wrap[action-type="feed_list_item"]'):
        mid = card.attrib.get('mid', '')
        if not re.fullmatch(r'\d{6,}', mid):
            continue
        content = card.css('[node-type="feed_list_content_full"], [node-type="feed_list_content"]')
        text = clean(' '.join(content.xpath('.//text()').getall()))
        date_link = card.css('.from a').first
        raw_date = date_link.attrib.get('title') or clean(' '.join(date_link.xpath('.//text()').getall())) if date_link else ''
        records.append({'platform': 'weibo', 'id': mid, 'sourceUrl': 'https://weibo.com/2/detail/' + mid,
                        'title': text[:200], 'summary': text[:120], 'content': text,
                        'publishedAt': weibo_date(raw_date, now),
                        'author': clean(card.css('.name::text').get()),
                        'mediaType': 'video' if card.css('video, .WB_video, .media-video') else 'image',
                        'images': image_urls(card.css('.media-piclist img::attr(src), .WB_pic img::attr(src)').getall())})
    return records


def walk_posts(value):
    if isinstance(value, dict):
        if value.get('aweme_id') and value.get('create_time') and value.get('desc'):
            yield value
        else:
            for child in value.values():
                yield from walk_posts(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk_posts(child)


def parse_douyin(payloads):
    records, seen = [], set()
    for payload in payloads:
        for post in walk_posts(payload):
            mid = str(post['aweme_id'])
            if not re.fullmatch(r'\d{6,}', mid) or mid in seen:
                continue
            seen.add(mid)
            images = []
            for image in post.get('images') or []:
                if isinstance(image, dict):
                    images.extend(image.get('url_list') or [])
            text = clean(post['desc'])
            records.append({'platform': 'douyin', 'id': mid, 'sourceUrl': 'https://www.douyin.com/note/' + mid,
                            'title': text[:200], 'summary': text[:120], 'content': text,
                            'publishedAt': post['create_time'],
                            'author': clean((post.get('author') or {}).get('nickname')),
                            'mediaType': 'image' if images else 'video', 'images': image_urls(images)})
    return records


def search_xiaohongshu(keyword, real_chrome=False):
    from scrapling.fetchers import DynamicFetcher
    page = DynamicFetcher.fetch('https://www.xiaohongshu.com/search_result?keyword=' + quote(keyword),
                                headless=True, real_chrome=real_chrome, disable_resources=True,
                                timeout=30000, wait=3000, locale='zh-CN', google_search=False)
    if page.status != 200:
        raise ValueError('search-http-error')
    state = initial_state(page)
    cards = state.get('search', {}).get('feeds')
    if isinstance(cards, dict):
        cards = cards.get('_value')
    if not isinstance(cards, list):
        raise ValueError('search-content-unavailable')
    if not cards:
        body = clean(' '.join(page.css('body').xpath('.//text()[not(ancestor::script) and not(ancestor::style)]').getall()))
        if not any(marker in body for marker in ('没有找到相关', '暂无相关', '无搜索结果')):
            raise ValueError('search-content-unavailable')
    records, attempted = [], 0
    for card in cards:
        note = card.get('noteCard', {})
        if note.get('type') != 'normal':
            continue
        mid = card.get('id')
        token = card.get('xsecToken')
        if not mid or not token:
            continue
        if attempted >= 3:
            break
        attempted += 1
        canonical = 'https://www.xiaohongshu.com/explore/' + str(mid)
        detail_page = DynamicFetcher.fetch(canonical + '?xsec_token=' + quote(token) + '&xsec_source=pc_search',
                                            headless=True, real_chrome=real_chrome, disable_resources=True,
                                            timeout=12000, wait=1000, locale='zh-CN', google_search=False)
        if detail_page.status != 200:
            continue
        detail = initial_state(detail_page).get('note', {}).get('noteDetailMap', {}).get(mid, {}).get('note', {})
        if not detail or detail.get('type') != 'normal':
            continue
        records.append({'platform': 'xiaohongshu', 'sourceUrl': canonical,
                        'title': clean(detail.get('title') or detail.get('desc') or '')[:200],
                        'summary': clean(detail.get('desc'))[:120], 'content': clean(detail.get('desc')),
                        'publishedAt': detail.get('time'), 'mediaType': 'image',
                        'images': image_urls(detail.get('imageList', [])),
                        'author': clean(detail.get('user', {}).get('nickname'))})
    if attempted and not records:
        raise ValueError('note-detail-unavailable')
    return records


def search(platform, keyword, now, real_chrome=False):
    if platform == 'xiaohongshu':
        return search_xiaohongshu(keyword, real_chrome)
    from scrapling.fetchers import DynamicFetcher
    payloads = []
    def setup(page):
        def response_received(response):
            # Only public search responses loaded by the page itself. No cookie/token extraction.
            url = urlparse(response.url)
            if url.hostname == 'www.douyin.com' and '/search/' in url.path and 'json' in response.headers.get('content-type', ''):
                try:
                    payloads.append(response.json())
                except Exception:
                    pass
        page.on('response', response_received)
    url = ('https://s.weibo.com/weibo?q=' + quote(keyword) + '&xsort=time') if platform == 'weibo' else ('https://www.douyin.com/search/' + quote(keyword) + '?type=general')
    page = DynamicFetcher.fetch(url, headless=True, real_chrome=real_chrome, disable_resources=True,
                                timeout=30000, wait=4000, locale='zh-CN', page_setup=setup,
                                google_search=False)
    if page.status != 200:
        raise ValueError('search-http-error')
    if platform == 'weibo':
        if urlparse(page.url).hostname not in {'s.weibo.com', 'weibo.com'}:
            raise ValueError('login-or-verification-required')
        cards = page.css('.card-wrap[action-type="feed_list_item"]')
        if not cards and not page.css('.card-no-result'):
            raise ValueError('search-content-unavailable')
        return parse_weibo(page, now)
    for script in page.css('script::text').getall():
        if 'aweme_id' not in script:
            continue
        try:
            payloads.append(json.loads(unquote(script)))
        except (ValueError, TypeError):
            pass
    valid_payloads = [p for p in payloads if isinstance(p, dict) and p.get('status_code', 0) == 0]
    records = parse_douyin(valid_payloads)
    if not records and not any(p.get('status_code') == 0 and any(key in p for key in ('aweme_list', 'data')) for p in valid_payloads):
        raise ValueError('search-content-unavailable')
    return records


def collect(platform, output, keyword='穿搭', real_chrome=False, search_fn=search):
    now = datetime.now(UTC)
    report = {'platform': platform, 'attemptedAt': now.isoformat(), 'windowHours': 24,
              'acceptedCount': 0, 'rejectedCount': 0}
    try:
        candidates = search_fn(platform, keyword, now, real_chrome)
        now = datetime.now(UTC)
        accepted, seen = [], set()
        for item in candidates:
            if recent(item, now) or item.get('platform') != platform or not item.get('title'):
                report['rejectedCount'] += 1
                continue
            record = feed_item(item, now)
            if record['id'] not in seen:
                accepted.append(record)
                seen.add(record['id'])
        accepted = accepted[:50]
        report.update(state='ready', acceptedCount=len(accepted), completedAt=now.isoformat())
        # Write the complete result atomically, including a legitimate zero-result search.
        atomic_write(output / (platform + '.json'), {'items': accepted})
        atomic_write(output / (platform + '-collection-status.json'), report)
        return {'items': accepted}
    except Exception as exc:
        # Preserve the last export on failures; never log response bodies or credentials.
        report.update(state='unavailable', error=type(exc).__name__,
                      reason=str(exc) if str(exc) in {'search-http-error', 'login-or-verification-required', 'search-content-unavailable', 'note-detail-unavailable'} else 'collector-failed')
        atomic_write(output / (platform + '-collection-status.json'), report)
        raise RuntimeError('public-source-unavailable') from None


def atomic_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
    temporary.replace(path)


def serve(output, host, port, real_chrome=False):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            platform = self.path.removeprefix('/').removesuffix('.json')
            if platform not in PLATFORMS:
                self.send_error(404)
                return
            try:
                data = collect(platform, output, real_chrome=real_chrome)
                body = json.dumps(data, ensure_ascii=False).encode('utf-8')
                self.send_response(200)
            except RuntimeError:
                body = b'{"error":"public-source-unavailable"}'
                self.send_response(503)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        def log_message(self, *_):
            pass
    HTTPServer((host, port), Handler).serve_forever()


if __name__ == '__main__':
    logging.disable(logging.INFO)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path(__file__).parent / 'data')
    parser.add_argument('--serve', action='store_true')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8766)
    parser.add_argument('--real-chrome', action='store_true')
    args = parser.parse_args()
    if args.serve:
        serve(args.output, args.host, args.port, args.real_chrome)
    else:
        for platform in sorted(PLATFORMS):
            try:
                data = collect(platform, args.output, real_chrome=args.real_chrome)
                print(platform, 'accepted', len(data['items']))
            except RuntimeError:
                print(platform, 'unavailable; see collection status')
