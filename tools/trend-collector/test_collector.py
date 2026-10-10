from contextlib import redirect_stdout
from datetime import datetime, timedelta, timezone
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from collector import MAX_FEED_BYTES, normalize, count, report, write_feed, main


class CollectorTests(unittest.TestCase):
    def row(self):
        now = datetime.now(timezone.utc).timestamp()
        return {"note_id": "abc", "title": "极简通勤穿搭", "time": now * 1000, "last_modify_ts": now * 1000,
                "image_list": "https://img.example/a.jpg,https://img.example/b.jpg", "liked_count": "123",
                "note_url": "https://weibo.com/detail/abc"}

    def weibo_row(self, item_id, timestamp, title):
        return {"note_id": item_id, "title": title, "time": timestamp, "last_modify_ts": timestamp,
                "note_url": f"https://weibo.com/detail/{item_id}"}

    def test_xiaohongshu_is_not_an_importable_platform(self):
        with self.assertRaises(ValueError):
            normalize(self.row(), "xiaohongshu")
        with patch("sys.argv", ["collector.py", "import", "--platform", "xiaohongshu"]), redirect_stdout(io.StringIO()):
            with self.assertRaises(SystemExit):
                main()

    def test_preserves_gallery_and_unknown_counters(self):
        item = normalize(self.row(), "weibo")
        self.assertEqual(len(item["evidence"]["images"]), 2)
        self.assertEqual(item["evidence"]["likes"], 123)
        self.assertIsNone(item["evidence"]["favorites"])
        self.assertIsNone(count("1.2万"))

    def test_preserves_rounded_follower_label_without_inventing_an_exact_count(self):
        item = normalize({**self.row(), "author_followers_count": "23.2万"}, "weibo")
        self.assertIsNone(item["evidence"]["authorFollowers"])
        self.assertEqual(item["evidence"]["authorFollowersLabel"], "23.2万")

    def test_accepts_public_sina_weibo_mirror_only_for_weibo(self):
        mirrored = {**self.row(), "note_url": "https://www.sina.cn/news/detail/123.html"}
        self.assertEqual(normalize(mirrored, "weibo")["sourceUrl"], mirrored["note_url"])
        with self.assertRaises(ValueError):
            normalize({**mirrored, "aweme_id": "123", "aweme_url": mirrored["note_url"]}, "douyin")

    def test_rejects_undated_irrelevant_or_wrong_platform_content(self):
        for update in [{"time": None}, {"last_modify_ts": None}, {"title": "数码评测"}, {"note_url": "https://evil.example/a"}]:
            with self.subTest(update=update), self.assertRaises(ValueError):
                normalize({**self.row(), **update}, "weibo")

    def test_rejects_red_carpet_runway_and_celebrity_editorial_content(self):
        for title in ["艾美奖红毯礼服造型", "2026 春夏时装周秀场趋势", "明星时装大片造型",
                      "Vogue 杂志封面穿搭", "秋季时尚画报大片"]:
            with self.subTest(title=title), self.assertRaises(ValueError):
                normalize({**self.row(), "title": title}, "weibo")

    def test_only_explicitly_reviewed_image_enters_gallery_contract(self):
        row = self.row()
        self.assertIsNone(normalize(row, "weibo")["evidence"]["fullBodyImageUrl"])
        reviewed = {**row, "full_body_image_url": "https://img.example/b.jpg",
                    "full_body_image_verified": True}
        self.assertEqual(normalize(reviewed, "weibo")["evidence"]["fullBodyImageUrl"],
                         "https://img.example/b.jpg")
        unknown = {**reviewed, "full_body_image_url": "https://img.example/other.jpg"}
        self.assertIsNone(normalize(unknown, "weibo")["evidence"]["fullBodyImageUrl"])

    def test_deduplicates_and_preserves_previous_feed_on_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)
            row = self.row()
            write_feed([row, row], "weibo", output)
            path = output / "weibo.json"
            before = path.read_bytes()
            self.assertEqual(len(json.loads(before)["items"]), 1)
            with self.assertRaises(ValueError):
                write_feed([], "weibo", output)
            self.assertEqual(path.read_bytes(), before)

    def test_import_merges_multiple_files_and_keeps_newest_duplicate(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            now = datetime.now(timezone.utc).timestamp()
            first_file = root / "first.json"
            second_file = root / "second.json"
            first_file.write_text(json.dumps([
                self.weibo_row("first", now - 30, "第一条通勤穿搭"),
                self.weibo_row("shared", now - 20, "旧版穿搭"),
            ]), encoding="utf-8")
            second_file.write_text(json.dumps([
                self.weibo_row("second", now - 10, "第二条通勤穿搭"),
                self.weibo_row("shared", now - 5, "更新版穿搭"),
            ]), encoding="utf-8")
            output = root / "data"
            argv = ["collector.py", "import", "--input", str(first_file), str(second_file),
                    "--platform", "weibo", "--output", str(output)]

            with patch("sys.argv", argv), redirect_stdout(io.StringIO()):
                main()

            items = json.loads((output / "weibo.json").read_text(encoding="utf-8"))["items"]
            self.assertCountEqual([item["id"] for item in items], ["first", "second", "shared"])
            shared = next(item for item in items if item["id"] == "shared")
            self.assertEqual(shared["title"], "更新版穿搭")

    def test_invalid_later_input_preserves_existing_feed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            output = root / "data"
            now = datetime.now(timezone.utc).timestamp()
            write_feed([self.weibo_row("existing", now - 100, "已有通勤穿搭")], "weibo", output)
            target = output / "weibo.json"
            before = target.read_bytes()

            first_file = root / "first.json"
            second_file = root / "invalid.json"
            first_file.write_text(json.dumps([
                self.weibo_row("replacement", now - 10, "替换穿搭")
            ]), encoding="utf-8")
            second_file.write_text("not json", encoding="utf-8")
            argv = ["collector.py", "import", "--input", str(first_file), str(second_file),
                    "--platform", "weibo", "--output", str(output)]

            with patch("sys.argv", argv), redirect_stdout(io.StringIO()):
                with self.assertRaises(json.JSONDecodeError):
                    main()

            self.assertEqual(target.read_bytes(), before)

    def test_douyin_cover_and_weibo_text_without_fabricated_photo(self):
        row = self.row()
        douyin = normalize({**row, "aweme_id": "123", "aweme_url": "https://www.douyin.com/video/123", "image_list": "", "cover_url": "https://img.example/cover.jpg"}, "douyin")
        self.assertEqual(douyin["imageUrl"], "https://img.example/cover.jpg")
        weibo = normalize({**row, "note_url": "https://m.weibo.cn/detail/123", "image_list": ""}, "weibo")
        self.assertIsNone(weibo["imageUrl"])

    def statuses(self, output, now=None):
        with redirect_stdout(io.StringIO()):
            return {row["platform"]: row for row in report(output, now=now)}

    def test_status_counts_recent_items_and_marks_old_feed_stale(self):
        fixed_now = datetime(2026, 9, 29, 12, 0, tzinfo=timezone.utc)
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)
            old = self.weibo_row("old", (fixed_now - timedelta(days=8)).timestamp(), "旧的通勤穿搭")
            with redirect_stdout(io.StringIO()):
                write_feed([old], "weibo", output, now=fixed_now)
            stale = self.statuses(output, now=fixed_now)["weibo"]
            self.assertEqual(stale["state"], "stale")
            self.assertEqual(stale["eligibleItems"], 0)
            self.assertIn("不会进入最近 7 天趋势", stale["note"])

            fresh = self.weibo_row("fresh", (fixed_now - timedelta(days=2)).timestamp(), "新的通勤穿搭")
            with redirect_stdout(io.StringIO()):
                write_feed([fresh], "weibo", output, now=fixed_now)
            ready = self.statuses(output, now=fixed_now)["weibo"]
            self.assertEqual(ready["state"], "ready")
            self.assertEqual(ready["eligibleItems"], 1)
            self.assertEqual(ready["newestPublishedAt"],
                             (fixed_now - timedelta(days=2)).isoformat())

    def test_strict_import_rejects_stale_rows_without_overwriting_previous_feed(self):
        fixed_now = datetime(2026, 9, 29, 12, 0, tzinfo=timezone.utc)
        stale = self.weibo_row("old", (fixed_now - timedelta(days=8)).timestamp(), "旧的通勤穿搭")
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)
            with redirect_stdout(io.StringIO()):
                write_feed([stale], "weibo", output, now=fixed_now)
            target = output / "weibo.json"
            before = target.read_bytes()
            with self.assertRaisesRegex(ValueError, "last 168 hours"):
                write_feed([stale], "weibo", output, require_fresh_hours=168, now=fixed_now)
            self.assertEqual(target.read_bytes(), before)

    def test_default_import_remains_compatible_with_stale_rows(self):
        fixed_now = datetime(2026, 9, 29, 12, 0, tzinfo=timezone.utc)
        stale = self.weibo_row("old", (fixed_now - timedelta(days=8)).timestamp(), "旧的通勤穿搭")
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)
            with redirect_stdout(io.StringIO()) as captured:
                write_feed([stale], "weibo", output, now=fixed_now)
            result = json.loads(captured.getvalue())
            self.assertEqual(result["items"], 1)
            self.assertEqual(result["freshItems"], 0)
            self.assertTrue((output / "weibo.json").is_file())

    def test_status_distinguishes_missing_ready_oversized_and_unreadable_feeds(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)
            self.assertEqual(
                {row["state"] for row in self.statuses(output).values()}, {"missing"},
                "an unimported source must be reported as missing, not as an empty feed")
            self.assertIsNone(self.statuses(output)["douyin"]["writtenAt"])

            write_feed([self.row()], "weibo", output)
            ready = self.statuses(output)["weibo"]
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
            write_feed([self.row()], "weibo", output)
            (output / "weibo.json").write_text(json.dumps({"items": []}), encoding="utf-8")
            self.assertEqual(self.statuses(output)["weibo"]["state"], "empty")


if __name__ == "__main__":
    unittest.main()
