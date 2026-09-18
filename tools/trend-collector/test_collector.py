from contextlib import redirect_stdout
from datetime import datetime, timezone
import io
import json
from pathlib import Path
import tempfile
import unittest
from collector import MAX_FEED_BYTES, normalize, count, report, write_feed


class CollectorTests(unittest.TestCase):
    def row(self):
        now = datetime.now(timezone.utc).timestamp()
        return {"note_id": "abc", "title": "极简通勤穿搭", "time": now * 1000, "last_modify_ts": now * 1000,
                "image_list": "https://img.example/a.jpg,https://img.example/b.jpg", "liked_count": "123",
                "note_url": "https://www.xiaohongshu.com/explore/abc"}

    def test_preserves_gallery_and_unknown_counters(self):
        item = normalize(self.row(), "xiaohongshu")
        self.assertEqual(len(item["evidence"]["images"]), 2)
        self.assertEqual(item["evidence"]["likes"], 123)
        self.assertIsNone(item["evidence"]["favorites"])
        self.assertIsNone(count("1.2万"))

    def test_rejects_undated_irrelevant_or_wrong_platform_content(self):
        for update in [{"time": None}, {"last_modify_ts": None}, {"title": "数码评测"}, {"note_url": "https://evil.example/a"}]:
            with self.subTest(update=update), self.assertRaises(ValueError):
                normalize({**self.row(), **update}, "xiaohongshu")

    def test_deduplicates_and_preserves_previous_feed_on_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)
            row = self.row()
            write_feed([row, row], "xiaohongshu", output)
            path = output / "xiaohongshu.json"
            before = path.read_bytes()
            self.assertEqual(len(json.loads(before)["items"]), 1)
            with self.assertRaises(ValueError):
                write_feed([], "xiaohongshu", output)
            self.assertEqual(path.read_bytes(), before)

    def test_douyin_cover_and_weibo_text_without_fabricated_photo(self):
        row = self.row()
        douyin = normalize({**row, "aweme_id": "123", "aweme_url": "https://www.douyin.com/video/123", "image_list": "", "cover_url": "https://img.example/cover.jpg"}, "douyin")
        self.assertEqual(douyin["imageUrl"], "https://img.example/cover.jpg")
        weibo = normalize({**row, "note_url": "https://m.weibo.cn/detail/123", "image_list": ""}, "weibo")
        self.assertIsNone(weibo["imageUrl"])

    def statuses(self, output):
        with redirect_stdout(io.StringIO()):
            return {row["platform"]: row for row in report(output)}

    def test_status_distinguishes_missing_ready_oversized_and_unreadable_feeds(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)
            self.assertEqual(
                {row["state"] for row in self.statuses(output).values()}, {"missing"},
                "an unimported source must be reported as missing, not as an empty feed")
            self.assertIsNone(self.statuses(output)["douyin"]["writtenAt"])

            write_feed([self.row()], "xiaohongshu", output)
            ready = self.statuses(output)["xiaohongshu"]
            self.assertEqual(ready["state"], "ready")
            self.assertEqual(ready["items"], 1)
            self.assertIsNotNone(ready["newestPublishedAt"])
            self.assertTrue(ready["withinBackendLimit"])

            (output / "weibo.json").write_text(
                json.dumps({"items": [{"pad": "x" * MAX_FEED_BYTES}]}), encoding="utf-8")
            oversized = self.statuses(output)["weibo"]
            self.assertEqual(oversized["state"], "oversized")
            self.assertFalse(oversized["withinBackendLimit"])

            (output / "douyin.json").write_text("not json", encoding="utf-8")
            self.assertEqual(self.statuses(output)["douyin"]["state"], "unreadable")

    def test_status_rejects_a_feed_without_usable_items(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)
            write_feed([self.row()], "xiaohongshu", output)
            (output / "xiaohongshu.json").write_text(json.dumps({"items": []}), encoding="utf-8")
            self.assertEqual(self.statuses(output)["xiaohongshu"]["state"], "empty")


if __name__ == "__main__":
    unittest.main()
