import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import patch, MagicMock
import collect_public as collector


class PublicCollectionTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026, 10, 8, 0, 0, tzinfo=timezone.utc)
        self.item = {'mediaType': 'image', 'images': ['https://example.com/look.jpg'],
                     'title': '秋季针织穿搭', 'summary': '', 'publishedAt': self.now.isoformat()}

    def test_window_is_inclusive_and_rejects_old_and_future(self):
        for age in (0, 7):
            self.item['publishedAt'] = (self.now - timedelta(days=age)).isoformat()
            self.assertIsNone(collector.eligible(self.item, self.now))
        for stamp in (self.now - timedelta(days=7, seconds=1), self.now + timedelta(seconds=1)):
            self.item['publishedAt'] = stamp.isoformat()
            self.assertEqual('outside-seven-day-window', collector.eligible(self.item, self.now))

    def test_qipao_is_fashion_but_old_posts_stay_excluded(self):
        self.item['title'] = '夏日旗袍'
        self.assertIsNone(collector.eligible(self.item, self.now))
        self.item['publishedAt'] = '2023-06-16T00:00:00Z'
        self.assertEqual('outside-seven-day-window', collector.eligible(self.item, self.now))

    def test_unknown_date_is_not_replaced_with_capture_time(self):
        self.item['publishedAt'] = None
        self.assertEqual('missing-or-invalid-publication-time', collector.eligible(self.item, self.now))

    def test_video_and_missing_images_are_rejected(self):
        self.item['mediaType'] = 'video'
        self.assertEqual('not-image-post', collector.eligible(self.item, self.now))
        self.item['mediaType'] = 'image'
        self.item['images'] = []
        self.assertEqual('missing-images', collector.eligible(self.item, self.now))

    def test_video_cards_never_trigger_detail_requests(self):
        page = MagicMock()
        page.css.return_value.getall.return_value = [
            'window.__INITIAL_STATE__={"feed":{"feeds":[{"id":"video-id","noteCard":{"type":"video","displayTitle":"穿搭"}}]}};']
        with patch.object(collector, 'fetch', return_value=page) as fetch:
            records, status = collector.xiaohongshu()
        self.assertEqual([], records)
        self.assertEqual(1, status['videoCardsSkipped'])
        self.assertEqual(1, fetch.call_count)

    def test_media_urls_exclude_video_assets(self):
        self.assertEqual(['https://example.com/look.jpg'], collector.image_urls([
            'https://example.com/look.jpg', 'https://example.com/clip.mp4?sig=abc',
            'https://example.com/clip.webm', 'https://example.com/look.jpg']))

    def test_unavailable_counters_and_full_body_verification_stay_unknown(self):
        item = dict(self.item, platform='weibo', sourceUrl='https://weibo.com/2/detail/1234567890')
        result = collector.feed_item(item, self.now)
        self.assertIsNone(result['evidence']['likes'])
        self.assertIsNone(result['evidence']['author'])
        self.assertIsNone(result['evidence']['fullBodyImageUrl'])
        self.assertEqual('image', result['evidence']['mediaType'])

    def test_excluded_topics_are_checked_beyond_the_short_summary(self):
        for excluded in ('演唱会', '明星同款', '巴黎时装周', 'Fashion Week', 'runway'):
            with self.subTest(excluded=excluded):
                self.item['content'] = '日常针织穿搭 ' * 40 + excluded
                self.assertEqual('outside-current-content-scope', collector.eligible(self.item, self.now))

    def test_full_content_is_not_exported_to_the_feed(self):
        item = dict(self.item, platform='weibo', sourceUrl='https://weibo.com/2/detail/123', content='原始正文')
        self.assertNotIn('content', collector.feed_item(item, self.now))

    def test_chinese_page_dates_preserve_timezone(self):
        self.assertEqual(datetime(2026, 10, 7, 2, 0, tzinfo=timezone.utc),
                         collector.publication('2026-10-07 10:00'))


if __name__ == '__main__':
    unittest.main()
