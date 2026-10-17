import unittest
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

from playwright.sync_api import TimeoutError as BrowserTimeout
import logged_in_weibo as collector


class LoggedInWeiboTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026, 10, 15, 2, 0, tzinfo=timezone.utc)
        self.href = 'https://weibo.com/1645752321/RmKs1nliW?refer_flag=public-search'
        self.canonical = self.href.split('?')[0]

    def card(self, *, video=False, title='通勤针织穿搭'):
        return {'href': self.href, 'html': '<div class="card-wrap" action-type="feed_list_item" mid="5354217775563442">'
                '<p node-type="feed_list_content">' + title + '</p>'
                '<div class="from"><a>11分钟前</a></div><div class="media-piclist">'
                '<img src="https://images.test/look.jpg"></div>'
                + ('<video></video>' if video else '') + '</div>'}

    def browser(self, engine, cards):
        browser = engine.return_value.__enter__.return_value.chromium.connect_over_cdp.return_value
        context = MagicMock()
        browser.contexts = [context]
        search, detail = MagicMock(), MagicMock()
        search.is_closed.return_value = detail.is_closed.return_value = False
        search.url = 'https://s.weibo.com/weibo'
        search.locator.return_value.evaluate_all.return_value = cards
        detail.locator.return_value.evaluate_all.return_value = [
            {'href': 'https://weibo.com/1234567890/OtherPost', 'text': '26-10-14 00:00'},
            {'href': self.canonical, 'text': '26-10-15 09:40'}]
        context.new_page.side_effect = [search, detail]
        return browser, context, search, detail

    def test_accepts_only_local_browser_and_public_post_links(self):
        self.assertEqual('http://127.0.0.1:9222', collector.local_browser_url('http://127.0.0.1:9222'))
        for url in ('https://remote.test:9222', 'http://remote.test:9222', 'http://user:secret@localhost:9222'):
            with self.subTest(url=url), self.assertRaises(ValueError):
                collector.local_browser_url(url)
        self.assertEqual(self.canonical, collector.public_post_url(self.href))
        for url in ('https://weibo.com/account/settings', 'https://weibo.com.evil.test/1645752321/RmKs1nliW',
                    'https://weibo.com:bad/1645752321/RmKs1nliW', 'javascript:alert(1)', None):
            self.assertIsNone(collector.public_post_url(url))

    @patch.object(collector, 'sync_playwright')
    def test_uses_exact_date_from_same_original_and_keeps_user_session_open(self, engine):
        browser, context, search, detail = self.browser(engine, [self.card()])
        records = collector.search_logged_in('http://127.0.0.1:9222', '穿搭', self.now)
        self.assertEqual(1, len(records))
        self.assertEqual('2026-10-15T01:40:00+00:00', records[0]['publishedAt'])
        self.assertEqual(self.canonical, records[0]['sourceUrl'])
        self.assertIsNone(collector.recent(records[0], self.now))
        detail.goto.assert_called_once_with(self.href, wait_until='domcontentloaded', timeout=30000)
        self.assertEqual(2, context.new_page.call_count)
        search.close.assert_called_once()
        detail.close.assert_called_once()
        browser.close.assert_not_called()
        context.close.assert_not_called()
        context.cookies.assert_not_called()
        context.storage_state.assert_not_called()

    @patch.object(collector, 'sync_playwright')
    def test_video_and_excluded_content_never_load_original_details(self, engine):
        browser, context, search, detail = self.browser(engine, [self.card(video=True), self.card(title='明星穿搭')])
        collector.search_logged_in('http://127.0.0.1:9222', '穿搭', self.now)
        self.assertEqual(1, context.new_page.call_count)
        detail.goto.assert_not_called()
        search.close.assert_called_once()

    @patch.object(collector, 'sync_playwright')
    def test_login_wall_reports_failure_without_replacing_it_with_empty_success(self, engine):
        browser, context, search, detail = self.browser(engine, [])
        search.url = 'https://passport.weibo.com/sso/signin'
        search.wait_for_selector.side_effect = BrowserTimeout('login')
        with self.assertRaisesRegex(ValueError, 'login-or-verification-required'):
            collector.search_logged_in('http://127.0.0.1:9222', '穿搭', self.now)
        search.close.assert_called_once()
        browser.close.assert_not_called()

    def test_relative_time_stays_unknown_if_original_is_unavailable(self):
        detail = MagicMock()
        detail.wait_for_function.side_effect = BrowserTimeout('no date')
        self.assertIsNone(collector.read_original_date(detail, self.href, self.canonical, self.now))

    def test_resource_route_does_not_download_video_or_images(self):
        context = MagicMock()
        page = collector.new_public_page(context)
        handler = page.route.call_args.args[1]
        for kind in ('media', 'image', 'font', 'document', 'script'):
            route = MagicMock()
            route.request.resource_type = kind
            handler(route)
            if kind in ('media', 'image', 'font'):
                route.abort.assert_called_once()
                route.continue_.assert_not_called()
            else:
                route.continue_.assert_called_once()


if __name__ == '__main__':
    unittest.main()
