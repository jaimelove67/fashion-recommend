import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace
from datetime import datetime, timedelta, timezone
from scrapling.parser import Selector
import daily_collector as collector

class DailyCollectorTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime.now(timezone.utc)
        self.item = {'platform': 'weibo', 'title': '秋季针织穿搭', 'summary': '', 'publishedAt': self.now.isoformat(),
                     'mediaType': 'image', 'images': ['https://images.test/look.jpg'],
                     'sourceUrl': 'https://weibo.com/2/detail/1234567890'}

    def test_exact_24_hour_window_and_unknown_time(self):
        self.assertIsNone(collector.recent(self.item, self.now))
        self.item['publishedAt'] = (self.now - timedelta(hours=24)).isoformat()
        self.assertIsNone(collector.recent(self.item, self.now))
        self.item['publishedAt'] = (self.now - timedelta(hours=24, seconds=1)).isoformat()
        self.assertEqual('outside-24-hour-window', collector.recent(self.item, self.now))
        self.item['publishedAt'] = None
        self.assertEqual('missing-or-invalid-publication-time', collector.recent(self.item, self.now))

    def test_rejects_shows_and_celebrities_even_in_full_content(self):
        for text in ('巴黎时装周', '秀场走秀', '明星红毯', '时装大片', '杂志封面'):
            self.item['content'] = text
            self.assertEqual('outside-current-content-scope', collector.recent(self.item, self.now))

    def test_xiaohongshu_fetches_only_image_details_and_preserves_publication_time(self):
        state = {'search': {'feeds': [
            {'id': 'video-id', 'noteCard': {'type': 'video'}},
            {'id': 'image-id', 'xsecToken': 'public-card-token', 'noteCard': {'type': 'normal'}}]}}
        detail = {'note': {'noteDetailMap': {'image-id': {'note': {
            'type': 'normal', 'title': '针织穿搭', 'desc': '日常通勤搭配', 'time': int(self.now.timestamp() * 1000),
            'imageList': [{'urlDefault': 'https://images.test/look.jpg'}], 'user': {'nickname': '博主'}}}}}}
        def page(data):
            selector = Selector('<script>window.__INITIAL_STATE__=' + json.dumps(data) + ';</script>')
            return SimpleNamespace(status=200, css=selector.css)
        with patch('scrapling.fetchers.DynamicFetcher.fetch', side_effect=[page(state), page(detail)]) as fetch:
            records = collector.search_xiaohongshu('穿搭')
        self.assertEqual(2, fetch.call_count)
        self.assertEqual(1, len(records))
        self.assertEqual('xiaohongshu', records[0]['platform'])
        self.assertEqual('image', records[0]['mediaType'])
        self.assertNotIn('xsec_token', records[0]['sourceUrl'])
        self.assertIsNone(collector.recent(records[0], self.now))

    def test_weibo_card_preserves_real_date_and_image(self):
        page = Selector('<div class="card-wrap" action-type="feed_list_item" mid="1234567890"><p node-type="feed_list_content">针织穿搭</p><a class="name">穿搭博主</a><p class="from"><a title="2026-10-08 11:00">今天 11:00</a></p><div class="media-piclist"><img src="https://images.test/look.jpg"></div></div>')
        items = collector.parse_weibo(page, self.now)
        self.assertEqual(1, len(items))
        self.assertEqual('2026-10-08T03:00:00+00:00', items[0]['publishedAt'])
        self.assertEqual(['https://images.test/look.jpg'], items[0]['images'])
        self.assertEqual('穿搭博主', items[0]['author'])
        self.assertIsNone(collector.weibo_date('未知时间', self.now))

    def test_douyin_search_payload_deduplicates_and_rejects_video(self):
        post = {'aweme_id': '1234567890', 'create_time': int(self.now.timestamp()), 'desc': '秋季穿搭',
                'images': [{'url_list': ['https://images.test/look.jpg']}], 'author': {'nickname': '博主'}}
        items = collector.parse_douyin([{'data': [{'aweme_info': post}, {'aweme_info': post}]}])
        self.assertEqual(1, len(items))
        self.assertEqual('image', items[0]['mediaType'])
        post['images'] = []
        self.assertEqual('not-image-post', collector.recent(collector.parse_douyin([post])[0], self.now))

    def test_success_writes_feed_without_inventing_full_body_verification(self):
        with tempfile.TemporaryDirectory() as folder:
            data = collector.collect('weibo', Path(folder), search_fn=lambda *args: [self.item])
            self.assertEqual(1, len(data['items']))
            self.assertIsNone(data['items'][0]['evidence']['fullBodyImageUrl'])
            self.assertEqual(data, json.loads((Path(folder) / 'weibo.json').read_text(encoding='utf-8')))

    def test_failure_preserves_prior_feed_and_records_unavailable(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)
            (path / 'weibo.json').write_text('previous-feed', encoding='utf-8')
            def failed(*args):
                raise ValueError('login-or-verification-required')
            with self.assertRaises(RuntimeError):
                collector.collect('weibo', path, search_fn=failed)
            self.assertEqual('previous-feed', (path / 'weibo.json').read_text(encoding='utf-8'))
            status = json.loads((path / 'weibo-collection-status.json').read_text(encoding='utf-8'))
            self.assertEqual('unavailable', status['state'])
            self.assertEqual('login-or-verification-required', status['reason'])

    def test_successful_search_with_no_recent_posts_publishes_empty_result(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual({'items': []}, collector.collect('weibo', Path(folder), search_fn=lambda *args: []))

if __name__ == '__main__':
    unittest.main()
