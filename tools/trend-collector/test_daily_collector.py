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

    def test_weibo_visible_absolute_dates_and_today_without_space(self):
        now = datetime(2026, 10, 15, 2, 0, tzinfo=timezone.utc)
        for raw, expected in (
                ('今天01:32', '2026-10-14T17:32:00+00:00'),
                ('今天 09:40', '2026-10-15T01:40:00+00:00'),
                ('26-10-15 09:40', '2026-10-15T01:40:00+00:00')):
            with self.subTest(raw=raw):
                self.assertEqual(expected, collector.weibo_date(raw, now))
        # Relative labels do not provide an exact publication time; read the original post instead.
        self.assertIsNone(collector.weibo_date('11分钟前', now))
        self.assertIsNone(collector.weibo_date('40秒前 转赞人数超过50', now))

    def test_weibo_prefers_full_text_without_duplicating_collapsed_preview(self):
        page = Selector('<div class="card-wrap" action-type="feed_list_item" mid="1234567890">'
                        '<p node-type="feed_list_content">穿搭预览 展开</p>'
                        '<p node-type="feed_list_content_full">完整的针织穿搭正文</p></div>')
        self.assertEqual('完整的针织穿搭正文', collector.parse_weibo(page, self.now)[0]['content'])

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

    def test_default_queries_cover_audiences_and_style_without_labels_from_query(self):
        for platform in collector.PLATFORMS:
            with self.subTest(platform=platform):
                queries = []
                def searched(source_platform, query, *args):
                    queries.append((source_platform, query))
                    return [dict(self.item, platform=source_platform, author='男士穿搭哥特风博主')]
                with tempfile.TemporaryDirectory() as folder:
                    data = collector.collect(platform, Path(folder), search_fn=searched)
                self.assertEqual([(platform, query) for query in collector.DAILY_KEYWORDS], queries)
                self.assertEqual(1, len(data['items']))
                self.assertEqual(['穿搭灵感'], data['items'][0]['topicTags'])

    def test_explicit_keyword_preserves_existing_single_search_entrypoint(self):
        queries = []
        with tempfile.TemporaryDirectory() as folder:
            collector.collect('weibo', Path(folder), keyword='指定穿搭',
                              search_fn=lambda platform, query, *args: queries.append(query) or [])
        self.assertEqual(['指定穿搭'], queries)

    def test_candidate_limit_is_shared_not_five_per_platform_and_keeps_each_query(self):
        def searched(platform, query, *args):
            offset = collector.DAILY_KEYWORDS.index(query) * 100
            titles = ['男士衬衫穿搭', '女士针织穿搭', '哥特风穿搭']
            return [dict(self.item, id=str(1234567890 + offset + index),
                         title=titles[collector.DAILY_KEYWORDS.index(query)]) for index in range(50)]
        with tempfile.TemporaryDirectory() as folder:
            data = collector.collect('weibo', Path(folder), search_fn=searched)
            status = json.loads((Path(folder) / 'weibo-collection-status.json').read_text(encoding='utf-8'))
        self.assertEqual(50, len(data['items']))
        for tag in ('男装穿搭', '女装穿搭', '小众穿搭'):
            self.assertGreaterEqual(sum(tag in item['topicTags'] for item in data['items']), 16)
        self.assertEqual(3, status['queriesCompleted'])
        self.assertTrue(status['contentReviewRequired'])

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

    def test_later_query_failure_never_overwrites_feed_with_partial_categories(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)
            (path / 'weibo.json').write_text('previous-complete-feed', encoding='utf-8')
            with self.assertRaises(RuntimeError):
                collector.collect('weibo', path,
                                  search_fn=unittest.mock.Mock(side_effect=[[self.item],
                                      ValueError('search-content-unavailable')]))
            self.assertEqual('previous-complete-feed', (path / 'weibo.json').read_text(encoding='utf-8'))
            status = json.loads((path / 'weibo-collection-status.json').read_text(encoding='utf-8'))
        self.assertEqual(1, status['queriesCompleted'])
        self.assertEqual('unavailable', status['state'])

    def test_successful_search_with_no_recent_posts_publishes_empty_result(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual({'items': []}, collector.collect('weibo', Path(folder), search_fn=lambda *args: []))

if __name__ == '__main__':
    unittest.main()
