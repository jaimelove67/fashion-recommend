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

    def test_blocks_suggestive_copy_in_full_text_but_keeps_normal_half_body_and_item_details(self):
        for text in ('OOTD 胸部特写', '穿搭 擦边福利', '时尚 性暗示', 'OnlyFans 穿搭'):
            with self.subTest(text=text):
                self.item['content'] = '日常针织 ' * 100 + text
                self.assertEqual('body-focused-or-suggestive', collector.eligible(self.item, self.now))
        for text in ('正常半身针织衫穿搭', '衬衫胸前口袋细节', '珍珠项链搭配细节', '吊带配开衫', '大胸女生衬衫尺码与版型建议'):
            with self.subTest(text=text):
                self.item['content'] = text
                self.assertIsNone(collector.eligible(self.item, self.now))

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

    def test_exported_text_respects_java_utf16_limits_without_splitting_emoji(self):
        cases = (
            ('title', '穿搭' + '衣' * 194 + '😀😀尾尾', '穿搭' + '衣' * 194 + '😀😀', 200),
            ('title', '穿搭' + '衣' * 197 + '😀尾', '穿搭' + '衣' * 197, 200),
            ('title', '穿搭' + '😀' * 99, '穿搭' + '😀' * 99, 200),
            ('summary', '衣' * 599 + '😀尾', '衣' * 599, 600),
            ('summary', '衣' * 598 + '😀尾', '衣' * 598 + '😀', 600),
            ('author', '名' * 119 + '😀尾', '名' * 119, 120),
            ('author', '名' * 118 + '😀尾', '名' * 118 + '😀', 120),
        )
        for field, source, expected, limit in cases:
            with self.subTest(field=field, source_units=len(source.encode('utf-16-le')) // 2):
                item = dict(self.item, platform='weibo', sourceUrl='https://weibo.com/2/detail/1234567890',
                            **{field: source})
                result = collector.feed_item(item, self.now)
                exported = result['evidence'][field] if field == 'author' else result[field]
                self.assertEqual(expected, exported)
                self.assertLessEqual(len(exported.encode('utf-16-le')) // 2, limit)
                self.assertEqual(item['publishedAt'], result['publishedAt'])
                self.assertEqual(item['sourceUrl'], result['sourceUrl'])
                self.assertEqual(item['images'], result['evidence']['images'])

    def test_excluded_topics_are_checked_beyond_the_short_summary(self):
        for excluded in ('演唱会', '明星同款', '巴黎时装周', 'Fashion Week', 'runway'):
            with self.subTest(excluded=excluded):
                self.item['content'] = '日常针织穿搭 ' * 40 + excluded
                self.assertEqual('outside-current-content-scope', collector.eligible(self.item, self.now))

    def test_full_content_is_not_exported_to_the_feed(self):
        item = dict(self.item, platform='weibo', sourceUrl='https://weibo.com/2/detail/123', content='原始正文')
        self.assertNotIn('content', collector.feed_item(item, self.now))

    def test_outfit_categories_require_explicit_clothing_copy_and_specific_style(self):
        for value, expected in (
                ('男士衬衫穿搭', ['男装穿搭']),
                ('男士穿搭 衬衫配牛仔裤', ['男装穿搭']),
                ('女士穿搭 针织裙', ['女装穿搭']),
                ('男装与女装搭配对比', ['男女装']),
                ('这不是男装穿搭', []),
                ('并非女装穿搭', []),
                ('不是男装穿搭，而是女装穿搭', ['女装穿搭']),
                ('男装穿搭，男女同款', ['男女装']),
                ('男装穿搭，无性别穿搭', ['男女装']),
                ('男装穿搭，男女通穿', ['男女装']),
                ('男装穿搭，unisex outfit', ['男女装']),
                ('男装穿搭，不是男女同款', ['男装穿搭']),
                ('小众品牌日常通勤穿搭 粉丝少', []),
                ('日常穿搭，这不是哥特风', []),
                ('日常穿搭，并非蒸汽朋克', []),
                ('非洛丽塔穿搭', []),
                ('衬衫日常穿搭，在哥特建筑前拍照', []),
                ('通勤穿搭，背景是赛博朋克', []),
                ('不是哥特风，是蒸汽朋克穿搭', ['小众穿搭', '蒸汽朋克']),
                ('男装哥特风穿搭', ['男装穿搭', '小众穿搭', '哥特风']),
                ('洛丽塔穿搭欣赏', ['小众穿搭', '洛丽塔']),
                ('蒸汽朋克穿搭', ['小众穿搭', '蒸汽朋克'])):
            with self.subTest(value=value):
                self.assertEqual(expected, collector.outfit_topic_tags(value))

    def test_feed_labels_use_full_copy_but_never_author_name_or_followers(self):
        item = dict(self.item, platform='weibo', sourceUrl='https://weibo.com/2/detail/1234567890',
                    author='男装洛丽塔', authorFollowers=100, content='日常衣服 ' * 60 + '女士穿搭 赛博朋克')
        self.assertEqual(['女装穿搭', '小众穿搭', '赛博朋克', '穿搭灵感'], collector.feed_item(item, self.now)['topicTags'])
        item['content'] = '秋季通勤穿搭'
        self.assertEqual(['穿搭灵感'], collector.feed_item(item, self.now)['topicTags'])

    def test_audience_requires_outfit_target_not_photographer_or_viewer_role(self):
        for value, expected in (
                ('男生帮我拍的穿搭', []),
                ('女生拍摄的穿搭', []),
                ('男士摄影穿搭', []),
                ('女士眼中的穿搭', []),
                ('男生眼中的穿搭，衬衫配西裤', []),
                ('女生镜头下的穿搭', []),
                ('男生帮我拍的女装穿搭', ['女装穿搭']),
                ('女生帮我拍的男士衬衫穿搭', ['男装穿搭']),
                ('男生拍摄女生日常通勤穿搭', ['女装穿搭']),
                ('男士衬衫穿搭', ['男装穿搭']),
                ('女士针织衫搭配', ['女装穿搭']),
                ('男生日常穿搭', ['男装穿搭']),
                ('女生的通勤穿搭', ['女装穿搭'])):
            with self.subTest(value=value):
                self.assertEqual(expected, collector.outfit_topic_tags(value))
                item = dict(self.item, platform='weibo', sourceUrl='https://weibo.com/2/detail/1234567890',
                            title=value)
                self.assertEqual(expected + ['穿搭灵感'], collector.feed_item(item, self.now)['topicTags'])

    def test_full_copy_ambiguity_survives_short_export_and_never_emits_single_audience(self):
        for detail in ('男女同款', '无性别穿搭', '女装穿搭'):
            with self.subTest(detail=detail):
                item = dict(self.item, platform='weibo', sourceUrl='https://weibo.com/2/detail/1234567890',
                            title='男装穿搭', summary='男装穿搭', content='针织 ' * 100 + detail)
                result = collector.feed_item(item, self.now)
                self.assertEqual('男装穿搭', result['title'])
                self.assertEqual('男装穿搭', result['summary'])
                self.assertNotIn('content', result)
                self.assertEqual(['男女装', '穿搭灵感'], result['topicTags'])

    def test_weibo_id_is_stable_across_search_and_original_permalink(self):
        item = dict(self.item, id='5354217775563442', platform='weibo',
                    sourceUrl='https://weibo.com/1645752321/RmKs1nliW')
        from_browser = collector.feed_item(item, self.now)
        item['sourceUrl'] = 'https://weibo.com/2/detail/5354217775563442'
        self.assertEqual('weibo:5354217775563442', from_browser['id'])
        self.assertEqual(from_browser['id'], collector.feed_item(item, self.now)['id'])

    def test_chinese_page_dates_preserve_timezone(self):
        self.assertEqual(datetime(2026, 10, 7, 2, 0, tzinfo=timezone.utc),
                         collector.publication('2026-10-07 10:00'))


if __name__ == '__main__':
    unittest.main()
