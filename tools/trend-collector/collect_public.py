"""Collect public image/text fashion posts; preserve dates and never request video assets.
Run with the isolated Scrapling interpreter. Results are staged for review, not auto-published.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import logging
import time
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlencode, urljoin, urlparse
from content_scope import text_rejection

logging.disable(logging.INFO)
UTC = timezone.utc
CST = timezone(timedelta(hours=8))
FASHION = re.compile(r'穿搭|搭配|时尚|时髦|时装|服饰|衬衫|针织|牛仔|裙|裤|外套|旗袍|配饰|手镯|饰品|包包|鞋|ootd', re.I)
EXCLUDED = re.compile(r'红毯|时装周|秀场|走秀|大秀|fashion[ -]?week|runway|catwalk|时装大片|时尚大片|杂志|画报|封面拍摄|magazine|fashion editorial|演唱会|明星|艺人|演员|歌手|女星|男星|影后|影帝|偶像|名人|星同款', re.I)
AUDIENCE_OUTFIT_WORDS = r'(?:日常|通勤|休闲|运动|极简|复古|街头|春季|夏季|秋季|冬季|春夏|秋冬|衬衫|T恤|针织衫?|毛衣|卫衣|西装|夹克|外套|大衣|羽绒服|牛仔裤|阔腿裤|长裤|短裤|连衣裙|半裙|裙装|裤装|套装)'
OUTFIT_AUDIENCE = {
    '男装穿搭': re.compile(r'男装|男[士生](?:的)?' + AUDIENCE_OUTFIT_WORDS + r'{0,2}(?:穿搭|搭配)|\bmenswear\b|\bmen[’\']?s\s+(?:outfit|style|fashion)', re.I),
    '女装穿搭': re.compile(r'女装|女[士生](?:的)?' + AUDIENCE_OUTFIT_WORDS + r'{0,2}(?:穿搭|搭配)|\bwomenswear\b|\bwomen[’\']?s\s+(?:outfit|style|fashion)', re.I),
}
SHARED_AUDIENCE = re.compile(r'男女装|男女同款|男女通穿|男女同穿|男女都能穿|无性别(?:穿搭|服装|风格|款式)?|\bunisex\b', re.I)
# Named outfit styles provide a reviewable basis for the appreciation slot.
# A small audience, a niche brand or generic "小众" marketing is not style evidence.
NICHE_STYLES = {
    '哥特风': re.compile(r'哥特(?:风|式)?|\bgoth(?:ic)?\b', re.I),
    '洛丽塔': re.compile(r'洛丽塔|洛莉塔|\blolita\b', re.I),
    '蒸汽朋克': re.compile(r'蒸汽朋克|\bsteampunk\b', re.I),
    '赛博朋克': re.compile(r'赛博朋克|\bcyberpunk\b', re.I),
    '视觉系': re.compile(r'视觉系|\bvisual\s+kei\b', re.I),
    '暗黑先锋': re.compile(r'暗黑先锋', re.I),
}
NEGATED_LABEL = re.compile(r'(?:不是|并非|不走|不属于|不算|不喜欢|拒绝|告别|不穿|没有|不要)[^，。；！？!?;,.]{0,4}$|非$|\b(?:not|non)[ -]?(?:a )?$', re.I)
NON_OUTFIT_STYLE = r'(?:建筑|教堂|电影|小说|游戏|背景|家具|装修|音乐|配乐|滤镜)'


def clean(value):
    return re.sub(r'\s+', ' ', str(value or '')).strip()


def outfit_topic_tags(value):
    """Classify explicit clothing copy, never the creator's identity or follower count.

    These are candidate labels for the existing content review gate. Comparisons
    and shared outfits retain an ambiguity marker even when summaries are truncated.
    """
    value = clean(value)
    def affirmative_label(pattern, outfit_style=False):
        for match in pattern.finditer(value):
            before = value[max(0, match.start() - 12):match.start()]
            after = value[match.end():match.end() + 12]
            if NEGATED_LABEL.search(before):
                continue
            # Nearby references to architecture/media do not describe the clothes.
            if outfit_style and (re.match(r'(?:的)?' + NON_OUTFIT_STYLE, after)
                                 or re.search(NON_OUTFIT_STYLE + r'(?:是|为|的)?$', before)):
                continue
            return True
        return False
    audience = [tag for tag, pattern in OUTFIT_AUDIENCE.items() if affirmative_label(pattern)]
    tags = ['男女装'] if len(audience) > 1 or affirmative_label(SHARED_AUDIENCE) else audience
    styles = [tag for tag, pattern in NICHE_STYLES.items() if affirmative_label(pattern, outfit_style=True)]
    return tags + (['小众穿搭'] + styles if styles else [])


def publication(value):
    if not value:
        raise ValueError('missing-publication-time')
    if isinstance(value, (float, int)):
        return datetime.fromtimestamp(value / 1000 if value > 10**11 else value, UTC)
    value = str(value).strip().replace('Z', '+00:00')
    if re.fullmatch(r'\d{4}\.\d{2}\.\d{2}', value):
        value = value.replace('.', '-')
    stamp = datetime.fromisoformat(value)
    return stamp.replace(tzinfo=CST).astimezone(UTC) if stamp.tzinfo is None else stamp.astimezone(UTC)


def eligible(item, now):
    if item.get('mediaType') != 'image':
        return 'not-image-post'
    if not item.get('images'):
        return 'missing-images'
    text = ' '.join(str(item.get(key) or '') for key in ('title', 'summary', 'content', 'author'))
    if text_rejection(text):
        return text_rejection(text)
    if not FASHION.search(text):
        return 'not-fashion'
    if EXCLUDED.search(text):
        return 'outside-current-content-scope'
    try:
        stamp = publication(item.get('publishedAt'))
    except (ValueError, TypeError, OverflowError, OSError):
        return 'missing-or-invalid-publication-time'
    if not now - timedelta(days=7) <= stamp <= now:
        return 'outside-seven-day-window'
    return None


def initial_state(page):
    for script in page.css('script::text').getall():
        marker = 'window.__INITIAL_STATE__='
        if marker in script:
            raw = script.split(marker, 1)[1].strip().rstrip(';')
            # Replace JS undefined tokens without modifying quoted text; never execute page JS.
            raw = re.sub(r'"(?:[^"\\]|\\.)*"|\bundefined\b',
                         lambda m: 'null' if m.group(0) == 'undefined' else m.group(0), raw)
            return json.loads(raw)
    return {}


def image_urls(values):
    result = []
    for value in values:
        if isinstance(value, dict):
            value = value.get('urlDefault') or value.get('urlPre') or value.get('url')
        if not isinstance(value, str):
            continue
        if value.startswith('//'):
            value = 'https:' + value
        if value.startswith(('http://', 'https://')) and not re.search(r'\.(mp4|webm|mov)(?:[?#]|$)', value, re.I) and value not in result:
            result.append(value)
    return result[:20]


def fetch(url):
    from scrapling.fetchers import Fetcher
    time.sleep(0.5)
    page = Fetcher.get(url, timeout=15, retries=0)
    if page.status != 200:
        raise ValueError('http-' + str(page.status))
    return page


def weibo(url):
    page = fetch(url)
    body = page.css('[class*="htmlText"]')
    if not body:
        raise ValueError('post-body-unavailable')
    container = body[0].xpath('..')[0]
    text = clean(' '.join(body[0].xpath('.//text()').getall()))
    published = page.css('meta[property="article:published_time"]::attr(content)').get()
    title = clean(page.css('meta[property="og:title"]::attr(content), title::text').get())
    if title in ('微博正文', ''):
        title = text[:70]
    video = bool(container.css('video,[class*="video"],[class*="Video"]'))
    return {'platform': 'weibo', 'sourceUrl': url, 'title': title[:200],
            'summary': text[:120], 'content': text, 'publishedAt': published,
            'mediaType': 'video' if video else 'image',
            'images': image_urls(container.css('img::attr(src)').getall()),
            'author': clean(page.css('[class*="userName"]::text,[class*="nickname"]::text').get())}


def vogue(url):
    from scrapling.parser import Selector
    page = fetch(url)
    raw = page.css('script#__NUXT_DATA__::text').get()
    if not raw:
        raise ValueError('article-data-unavailable')
    table = json.loads(raw)
    title = clean(page.css('h1::text').get())
    record = next((v for v in table if isinstance(v, dict) and 'publish_time' in v
                   and 'title' in v and table[v['title']] == title), None)
    if not record:
        raise ValueError('article-publication-time-unavailable')
    def field(name):
        index = record.get(name)
        return table[index] if isinstance(index, int) and 0 <= index < len(table) else None
    content = str(field('content') or '')
    fragment = Selector(content=content)
    paragraphs = [clean(' '.join(p.xpath('.//text()').getall())) for p in fragment.css('p')]
    text = next((p for p in paragraphs if len(p) > 20), '')
    images = image_urls(fragment.css('img::attr(data-src),img::attr(src)').getall())
    return {'platform': 'vogue', 'sourceUrl': url, 'title': title[:200],
            'summary': text[:120], 'content': ' '.join(paragraphs), 'publishedAt': field('publish_time'),
            'mediaType': 'video' if fragment.css('video,iframe') else 'image',
            'images': images, 'author': 'VOGUE中国'}


def xiaohongshu(limit=3):
    page = fetch('https://www.xiaohongshu.com/explore?channel_id=homefeed.fashion_v3')
    state = initial_state(page)
    cards = state.get('feed', {}).get('feeds', [])
    records, errors, skipped_video = [], [], 0
    for card in cards:
        note = card.get('noteCard', {})
        if note.get('type') == 'video':
            skipped_video += 1
            continue
        if note.get('type') != 'normal':
            continue
        if len(records) + len(errors) >= limit:
            continue
        note_id = card.get('id')
        token = card.get('xsecToken')
        if not note_id or not token:
            continue
        url = 'https://www.xiaohongshu.com/explore/' + note_id + '?' + urlencode({'xsec_token': token, 'xsec_source': 'pc_feed'})
        try:
            detail_page = fetch(url)
            detail = initial_state(detail_page).get('note', {}).get('noteDetailMap', {}).get(note_id, {}).get('note', {})
            if not detail:
                raise ValueError('note-detail-unavailable')
            if detail.get('type') != 'normal':
                skipped_video += 1
                continue
            records.append({'platform': 'xiaohongshu', 'sourceUrl': url,
                            'title': str(detail.get('title') or '')[:200],
                            'summary': str(detail.get('desc') or '')[:120],
                            'content': str(detail.get('desc') or ''),
                            'publishedAt': detail.get('time'), 'mediaType': 'image',
                            'images': image_urls(detail.get('imageList', [])),
                            'author': detail.get('user', {}).get('nickname', '')})
        except Exception as exc:
            errors.append({'sourceUrl': 'https://www.xiaohongshu.com/explore/' + note_id, 'reason': str(exc)})
    return records, {'publicCardCount': len(cards), 'videoCardsSkipped': skipped_video, 'detailErrors': errors}


def feed_item(item, now):
    identifier = str(item.get('id') or '')
    if item['platform'] == 'weibo':
        identifier = identifier if re.fullmatch(r'\d{6,}', identifier) else urlparse(item['sourceUrl']).path.rsplit('/', 1)[-1]
    else:
        identifier = hashlib.sha256(item['sourceUrl'].split('?')[0].encode()).hexdigest()[:24]
    # Use full original copy before truncation; author names and query terms are not evidence.
    tags = outfit_topic_tags(' '.join(str(item.get(key) or '') for key in ('title', 'summary', 'content', 'topicTags')))
    return {'id': item['platform'] + ':' + identifier,
            'platform': item['platform'], 'title': item['title'], 'summary': item['summary'],
            'topicTags': tags + ['穿搭灵感'], 'heatScore': 0,
            'publishedAt': publication(item['publishedAt']).isoformat(), 'fetchedAt': now.isoformat(),
            'sourceUrl': item['sourceUrl'], 'imageUrl': item['images'][0],
            'evidence': {'author': item.get('author') or None, 'mediaType': 'image', 'images': item['images'],
                         'likes': None, 'favorites': None, 'comments': None, 'reposts': None,
                         'authorFollowers': None, 'fullBodyImageUrl': None}}


def collect(output):
    now = datetime.now(UTC)
    output.mkdir(parents=True, exist_ok=True)
    candidates, errors = [], []
    for url in ['https://weibo.com/2/detail/5346697823915040',
                'https://weibo.com/2/detail/5350851455484714',
                'https://weibo.com/2/detail/5349801751216296']:
        try:
            candidates.append(weibo(url))
        except Exception as exc:
            errors.append({'sourceUrl': url, 'reason': str(exc)})
    xhs_status = {}
    try:
        items, xhs_status = xiaohongshu()
        candidates.extend(items)
    except Exception as exc:
        xhs_status = {'error': str(exc)}
    try:
        listing = fetch('https://www.vogue.com.cn/fashion/vogue/')
        links = []
        for href in listing.css('a::attr(href)').getall():
            url = urljoin(listing.url, href)
            if url.startswith('https://www.vogue.com.cn/fashion/vogue/news_') and url not in links:
                links.append(url)
        for url in links[:8]:
            try:
                candidates.append(vogue(url))
            except Exception as exc:
                errors.append({'sourceUrl': url, 'reason': str(exc)})
    except Exception as exc:
        errors.append({'platform': 'vogue', 'reason': str(exc)})
    accepted, rejected = [], []
    for item in candidates:
        reason = eligible(item, now)
        if reason:
            rejected.append({'platform': item['platform'], 'title': item['title'], 'sourceUrl': item['sourceUrl'].split('?')[0], 'publishedAt': item.get('publishedAt'), 'reason': reason})
        else:
            accepted.append(feed_item(item, now))
    report = {'fetchedAt': now.isoformat(), 'windowDays': 7, 'imageOnly': True,
              'acceptedCount': len(accepted), 'acceptedByPlatform': {p: sum(i['platform'] == p for i in accepted) for p in ('weibo', 'xiaohongshu', 'vogue')},
              'rejected': rejected, 'errors': errors, 'xiaohongshu': xhs_status}
    (output / 'feed.json').write_text(json.dumps({'items': accepted}, ensure_ascii=False, indent=2), encoding='utf-8')
    (output / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=True, indent=2))
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path(__file__).parent / 'data' / 'public-image-posts')
    collect(parser.parse_args().output)
