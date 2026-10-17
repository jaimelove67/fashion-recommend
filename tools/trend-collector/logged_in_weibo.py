"""Read public Weibo posts using an explicitly authorized, already logged-in browser.

Connect only to a local dedicated Chrome. Never export cookies, read private pages,
or close the browser. Output follows daily_collector's normal 24-hour feed contract.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
from urllib.parse import urlparse, urlunparse

from playwright.sync_api import sync_playwright, TimeoutError as BrowserTimeout, Error as BrowserError
from scrapling.parser import Selector

from daily_collector import collect, parse_weibo, recent, weibo_date

CARDS = '.card-wrap[action-type="feed_list_item"]'
TIME_LINKS = 'a[class*="_time_"]'
MAX_DETAILS = 10


def local_browser_url(value):
    url = urlparse(value)
    if (url.scheme != 'http' or url.hostname not in {'127.0.0.1', 'localhost', '::1'}
            or url.username or url.password or url.path not in {'', '/'} or url.query or url.fragment):
        raise ValueError('browser-url-must-be-local')
    return value


def public_post_url(value):
    try:
        url = urlparse(value or '')
        if (url.scheme != 'https' or url.hostname not in {'weibo.com', 'www.weibo.com'}
                or url.username or url.password or url.port not in {None, 443}
                or not re.fullmatch(r'/(?:\d{5,}/[A-Za-z0-9]+|2/detail/\d{6,})/?', url.path)):
            return None
        return urlunparse((url.scheme, url.netloc, url.path, '', '', ''))
    except ValueError:
        return None


def new_public_page(context):
    page = context.new_page()
    # Read image URLs from the DOM without downloading images or video files.
    page.route('**/*', lambda route: route.abort() if route.request.resource_type
               in {'image', 'media', 'font'} else route.continue_())
    return page


def read_original_date(page, href, canonical, now):
    try:
        page.goto(href, wait_until='domcontentloaded', timeout=30000)
        page.wait_for_function(r"""url => Array.from(document.querySelectorAll('a[class*="_time_"]'))
            .some(a => a.href.split(/[?#]/)[0] === url &&
                /\d{2}-\d{2}-\d{2} \d{1,2}:\d{2}/.test(a.textContent))""",
                               arg=canonical, timeout=10000)
        links = page.locator(TIME_LINKS).evaluate_all(
            'nodes => nodes.map(a => ({href: a.href, text: a.textContent.trim()}))')
        for link in links:
            if public_post_url(link['href']) == canonical:
                stamp = weibo_date(link['text'], now)
                if stamp:
                    return stamp
    except BrowserError:
        pass
    # Unknown dates stay unknown; do not subtract a rounded relative label from now.
    return None


def search_logged_in(browser_url, keyword, now):
    from urllib.parse import quote
    local_browser_url(browser_url)
    with sync_playwright() as playwright:
        try:
            browser = playwright.chromium.connect_over_cdp(browser_url, timeout=5000)
        except Exception:
            raise ValueError('authorized-browser-unavailable') from None
        if not browser.contexts:
            raise ValueError('authorized-browser-unavailable')
        context = browser.contexts[0]
        search_page, detail_page = None, None
        try:
            search_page = new_public_page(context)
            search_page.goto('https://s.weibo.com/weibo?q=' + quote(keyword) + '&xsort=time',
                             wait_until='domcontentloaded', timeout=30000)
            try:
                search_page.wait_for_selector(CARDS + ', .card-no-result', timeout=15000)
            except BrowserTimeout:
                reason = ('login-or-verification-required' if urlparse(search_page.url).hostname
                          != 's.weibo.com' else 'search-content-unavailable')
                raise ValueError(reason) from None
            cards = search_page.locator(CARDS).evaluate_all("""nodes => nodes.map(card => ({
                html: card.outerHTML, href: card.querySelector('.from a')?.href
            }))""")
            records, attempted = [], 0
            for card in cards:
                canonical = public_post_url(card.get('href'))
                if not canonical:
                    continue
                for record in parse_weibo(Selector(content=card['html']), now):
                    record['sourceUrl'] = canonical
                    if (recent(record, now) == 'missing-or-invalid-publication-time'
                            and attempted < MAX_DETAILS):
                        attempted += 1
                        if detail_page is None:
                            detail_page = new_public_page(context)
                        record['publishedAt'] = read_original_date(
                            detail_page, card['href'], canonical, now)
                    records.append(record)
            return records
        finally:
            for page in (detail_page, search_page):
                try:
                    if page is not None and not page.is_closed():
                        page.close()
                except BrowserError:
                    pass
            # Exiting Playwright disconnects its driver. Do not call browser.close()
            # or context.close(): those belong to the user's authorized session.


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--browser-url', default='http://127.0.0.1:9222')
    parser.add_argument('--keyword', default='穿搭')
    parser.add_argument('--output', type=Path, default=Path(__file__).parent / 'data' / 'logged-in-weibo')
    args = parser.parse_args()
    try:
        collect('weibo', args.output, keyword=args.keyword,
                search_fn=lambda _platform, keyword, now, _real_chrome:
                search_logged_in(args.browser_url, keyword, now))
    except RuntimeError:
        print('Weibo unavailable; see weibo-collection-status.json. Previous feed preserved.')
        return 1
    status = json.loads((args.output / 'weibo-collection-status.json').read_text(encoding='utf-8'))
    print(json.dumps(status, ensure_ascii=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
