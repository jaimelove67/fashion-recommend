import json
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch
from urllib.parse import parse_qs

from direct_api import APIError, build_query, load_session, parse, resolve_id
from extractor import find_work
from vendor.dtk_signing.sm3 import sm3_hexdigest

SESSION = {"user_agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/149.0.0.0 Safari/537.36",
           "cookies": [{"name": "UIFID_TEMP", "value": "test-visitor", "domain": ".douyin.com", "expires": -1}],
           "platform": "MacIntel", "screen_width": 1920, "screen_height": 1080}
URL = "https://www.douyin.com/video/7683406450030431498"
ID = "7683406450030431498"


class DirectTests(unittest.TestCase):
    def test_invalid_url_before_session_load(self):
        with self.assertRaises(APIError) as caught:
            parse("https://example.com/video/123", Path("missing-session.json"))
        self.assertEqual(caught.exception.code, "INVALID_URL")

    def test_connection_error_hides_signed_url(self):
        from curl_cffi import requests
        with tempfile.TemporaryDirectory() as folder, patch("direct_api.requests.Session") as factory:
            path = Path(folder) / "session.json"
            path.write_text(json.dumps(SESSION))
            factory.return_value.__enter__.return_value.get.side_effect = requests.errors.RequestsError("secret-cookie-in-url")
            with self.assertRaises(APIError) as caught:
                parse(URL, path)
            self.assertEqual(caught.exception.code, "UPSTREAM_CONNECTION")
            self.assertNotIn("secret-cookie", str(caught.exception))

    def test_sm3_known_vector(self):
        self.assertEqual(sm3_hexdigest(b"abc"), "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0")

    def test_query_matches_session_and_id(self):
        query, headers = build_query(ID, SESSION)
        values = parse_qs(query)
        self.assertEqual(values["aweme_id"], [ID])
        self.assertEqual(values["browser_version"], ["149.0.0.0"])
        self.assertEqual(values["uifid"], ["test-visitor"])
        self.assertTrue(values["a_bogus"])
        self.assertEqual(len(values["x-secsdk-web-signature"][0]), 32)

    def test_complete_url_needs_no_redirect_request(self):
        client = MagicMock()
        self.assertEqual(resolve_id(client, "分享 " + URL), ID)
        client.get.assert_not_called()

    def test_reject_external_redirect_even_with_valid_id(self):
        client = MagicMock()
        client.get.return_value.status_code = 302
        client.get.return_value.headers = {"location": f"http://127.0.0.1/video/{ID}"}
        with self.assertRaises(APIError) as caught:
            resolve_id(client, "https://v.douyin.com/test/")
        self.assertEqual(caught.exception.code, "INVALID_REDIRECT")
        self.assertEqual(client.get.call_count, 1)

    def test_short_link_resolves(self):
        client = MagicMock()
        client.get.return_value.status_code = 302
        client.get.return_value.headers = {"location": URL}
        self.assertEqual(resolve_id(client, "https://v.douyin.com/test/"), ID)

    def test_redirect_loop_is_bounded(self):
        client = MagicMock()
        client.get.return_value.status_code = 302
        client.get.return_value.headers = {"location": "https://v.douyin.com/test/"}
        with self.assertRaises(APIError):
            resolve_id(client, "https://v.douyin.com/test/")
        self.assertEqual(client.get.call_count, 6)

    def test_missing_or_expired_session(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "session.json"
            with self.assertRaises(APIError):
                load_session(path)
            data = json.loads(json.dumps(SESSION))
            data["cookies"][0]["expires"] = time.time() - 10
            path.write_text(json.dumps(data))
            with self.assertRaises(APIError) as caught:
                load_session(path)
            self.assertEqual(caught.exception.code, "SESSION_REQUIRED")

    def test_gallery_preserves_order_and_rejects_missing_images(self):
        item = {"aweme_id": ID, "images": [{"url_list": ["https://p.douyinpic.com/1"]},
                                          {"url_list": ["https://p.douyinpic.com/2"]}]}
        result = find_work({"aweme_detail": item}, ID)
        self.assertEqual([m["urls"][0] for m in result["assets"]], ["https://p.douyinpic.com/1", "https://p.douyinpic.com/2"])
        item["images"].append({})
        self.assertIsNone(find_work(item, ID))

    def test_http_parse_errors_are_safe_and_success_checks_id(self):
        with tempfile.TemporaryDirectory() as folder, patch("direct_api.requests.Session") as factory:
            path = Path(folder) / "session.json"
            path.write_text(json.dumps(SESSION))
            client = factory.return_value.__enter__.return_value
            response = client.get.return_value
            response.status_code, response.content = 200, b"json"
            response.json.return_value = {"aweme_detail": {"aweme_id": ID, "desc": "test", "video": {
                "play_addr": {"url_list": ["https://v.douyinvod.com/test"]}}}}
            result, _ = parse(URL, path)
            self.assertEqual(result["id"], ID)
            self.assertEqual(result["provider"], "douyin_web_api")
            response.json.return_value["aweme_detail"]["aweme_id"] = "123"
            with self.assertRaises(APIError) as caught:
                parse(URL, path)
            self.assertEqual(caught.exception.code, "MEDIA_UNAVAILABLE")
            response.status_code = 403
            with self.assertRaises(APIError) as caught:
                parse(URL, path)
            self.assertEqual(caught.exception.code, "UPSTREAM_REJECTED")
            self.assertNotIn("test-visitor", str(caught.exception))
