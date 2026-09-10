import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from extractor import (ExtractError, download_asset, find_work, media_extension,
                       checked_media_url, share_url, work_id)

VIDEO = {"aweme_id": "123456789", "desc": "测试视频", "author": {"nickname": "测试作者"},
         "video": {"play_addr": {"url_list": ["https://v.douyinvod.com/video.mp4"]}}}
GALLERY = {"aweme_id": "234567890", "desc": "测试图集", "images": [
    {"url_list": ["https://p.douyinpic.com/1.webp"]},
    {"url_list": ["https://p.douyinpic.com/2.webp"]}], "video": VIDEO["video"]}


class ExtractionTests(unittest.TestCase):
    def test_share_text_and_chinese_punctuation(self):
        self.assertEqual(share_url("3.21 复制此链接：https://v.douyin.com/AbCd/，打开抖音看视频"), "https://v.douyin.com/AbCd/")

    def test_long_gallery_and_modal_links(self):
        self.assertEqual(work_id(share_url("https://www.douyin.com/note/234567890")), "234567890")
        self.assertEqual(work_id(share_url("https://www.douyin.com/?modal_id=123456789")), "123456789")
        self.assertEqual(work_id("https://www.iesdouyin.com/share/video/123456789/"), "123456789")

    def test_reject_foreign_host_and_profile(self):
        for url in ["https://douyin.com.evil.test/video/123", "https://127.0.0.1/video/123", "file:///tmp/foo",
                    "https://www.douyin.com/user/123", "https://evil@www.douyin.com/video/123"]:
            with self.subTest(url=url), self.assertRaises(ExtractError):
                share_url(url)

    def test_expected_work_only(self):
        self.assertIsNone(find_work({"aweme_detail": VIDEO}, "999999999"))
        result = find_work({"feed": [VIDEO, GALLERY]}, "123456789")
        self.assertEqual(result["title"], "测试视频")
        self.assertEqual(result["type"], "视频")

    def test_gallery_does_not_download_soundtrack(self):
        result = find_work({"aweme_detail": GALLERY}, "234567890")
        self.assertEqual(len(result["assets"]), 2)
        self.assertTrue(all(a["kind"] == "image" for a in result["assets"]))

    def test_incomplete_gallery_not_reported_as_complete(self):
        self.assertIsNone(find_work({**GALLERY, "images": GALLERY["images"] + [{}]}, "234567890"))

    def test_camelcase_router_data(self):
        data = {"loaderData": {"video": {"awemeId": "123456789", "desc": "页面数据",
                                        "video": {"playAddr": {"urlList": ["https://v.douyinvod.com/a.mp4"]}}}}}
        self.assertEqual(find_work(data, "123456789")["type"], "视频")

    def test_nested_image_post(self):
        data = {"aweme_id": "123456789", "image_post_info": {"images": [
            {"display_image": {"url_list": ["https://p.douyinpic.com/a.jpg"]}}]}}
        self.assertEqual(find_work(data, "123456789")["type"], "图集")

    def test_format_validation(self):
        self.assertEqual(media_extension(b"RIFFxxxxWEBP", "image"), ".webp")
        self.assertEqual(media_extension(b"\x00\x00\x00\x20ftypisom", "video"), ".mp4")
        for content in [b"<html>login</html>", b'{"error": 403}', b""]:
            with self.assertRaises(ExtractError):
                media_extension(content, "video")

    def test_media_rejects_bad_hosts_and_private_dns(self):
        with self.assertRaises(ExtractError):
            checked_media_url("https://douyinvod.com.evil.test/a.mp4")
        with patch("extractor.socket.getaddrinfo", return_value=[(2, 1, 6, "", ("127.0.0.1", 443))]):
            with self.assertRaises(ExtractError):
                checked_media_url("https://v.douyinvod.com/a.mp4")


class FakeResponse(io.BytesIO):
    status = 200

    def __init__(self, body, length=None):
        super().__init__(body)
        self.headers = {"Content-Length": str(len(body) if length is None else length)}


class FakeOpener:
    def __init__(self, responses):
        self.responses = iter(responses)

    def open(self, *args, **kwargs):
        return next(self.responses)


class DownloadTests(unittest.TestCase):
    @patch("extractor.checked_media_url", side_effect=lambda u: u)
    def test_fallback_and_atomic_write(self, _):
        image = b"\x89PNG\r\n\x1a\n" + b"test" * 100
        opener = FakeOpener([FakeResponse(b"<html>expired</html>"), FakeResponse(image)])
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            result = download_asset({"kind": "image", "urls": ["https://a", "https://b"]}, folder, 1,
                                    opener, "test", lambda: False, lambda _: None)
            self.assertEqual((folder / "001.png").read_bytes(), image)
            self.assertEqual(result["size"], len(image))
            self.assertFalse((folder / "001.part").exists())

    @patch("extractor.checked_media_url", side_effect=lambda u: u)
    def test_truncated_response_not_saved(self, _):
        opener = FakeOpener([FakeResponse(b"\xff\xd8\xffabc", length=1000)])
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ExtractError, "不完整"):
                download_asset({"kind": "image", "urls": ["https://a"]}, Path(temp), 1, opener,
                               "test", lambda: False, lambda _: None)
            self.assertEqual(list(Path(temp).iterdir()), [])

    def test_cancelled_before_download(self):
        with tempfile.TemporaryDirectory() as temp, self.assertRaisesRegex(ExtractError, "取消"):
            download_asset({"kind": "video", "urls": ["https://a"]}, Path(temp), 1, None,
                           "test", lambda: True, lambda _: None)


if __name__ == "__main__":
    unittest.main()
