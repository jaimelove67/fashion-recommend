import json
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from api import create_app
from direct_api import APIError
from extractor import ExtractError
from test_direct_api import ID, SESSION, URL

KEY = "test-api-key-with-enough-characters"
RESULT = {"id": ID, "title": "test", "author": "author", "type": "视频", "source_url": URL,
          "provider": "douyin_web_api", "parsed_at": 1,
          "assets": [{"kind": "video", "urls": ["https://v.douyinvod.com/test?secret=signed"]}]}


class APITests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.session = self.root / "session.json"
        self.session.write_text(json.dumps(SESSION))
        self.calls = []

        def parser(*args):
            self.calls.append(args)
            return RESULT, SESSION

        self.app = create_app(session=self.session, output=self.root / "data", key=KEY, parser=parser)
        self.client = TestClient(self.app).__enter__()
        self.headers = {"Authorization": "Bearer " + KEY}

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.temp.cleanup()

    def test_auth_required_for_parse_status_and_files(self):
        for method, path in [("post", "/v1/parse"), ("get", "/v1/status"),
                             ("get", "/v1/downloads/unknown/files/test.mp4")]:
            kwargs = {"json": {"text": URL}} if method == "post" else {}
            self.assertEqual(getattr(self.client, method)(path, **kwargs).status_code, 401)
        self.assertFalse(self.calls)

    def test_parse_contract_no_session_leak(self):
        r = self.client.post("/v1/parse", json={"text": URL}, headers=self.headers)
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()["data"]["id"], ID)
        self.assertEqual(r.headers["cache-control"], "no-store")
        self.assertNotIn("test-visitor", r.text)
        self.assertNotIn("cookies", r.text)

    def test_request_validation_and_size_limit(self):
        for body in [{}, {"text": ""}, {"text": URL, "cookie": "no"}, {"text": [URL]}]:
            self.assertEqual(self.client.post("/v1/parse", json=body, headers=self.headers).status_code, 422)
        r = self.client.post("/v1/parse", content=b"x" * 17000, headers=self.headers)
        self.assertEqual(r.status_code, 413)
        self.assertFalse(self.calls)

    def test_docs_describe_bearer_auth(self):
        schema = self.client.get("/openapi.json").json()
        self.assertIn("HTTPBearer", schema["components"]["securitySchemes"])
        self.assertIn("/v1/parse", schema["paths"])

    def test_session_status_does_not_claim_live_success(self):
        r = self.client.get("/v1/status", headers=self.headers).json()
        self.assertTrue(r["session_configured"])
        self.assertFalse(r["upstream_verified"])

    def test_busy_does_not_call_upstream(self):
        with self.app.state.runtime.upstream:
            r = self.client.post("/v1/parse", json={"text": URL}, headers=self.headers)
        self.assertEqual(r.status_code, 429)
        self.assertFalse(self.calls)

    def test_upstream_error_releases_lock(self):
        original = self.app.state.runtime.parser
        def broken(*args):
            raise APIError("UPSTREAM_REJECTED", "请更新会话")
        self.app.state.runtime.parser = broken
        r = self.client.post("/v1/parse", json={"text": URL}, headers=self.headers)
        self.assertEqual(r.status_code, 502)
        self.app.state.runtime.parser = original
        self.assertEqual(self.client.post("/v1/parse", json={"text": URL}, headers=self.headers).status_code, 200)

    def test_download_job_and_file_and_restart(self):
        def download(asset, folder, index, *args):
            (folder / "001.mp4").write_bytes(b"test-media")
            return {"name": "001.mp4", "kind": "video", "size": 10}
        with patch("api.download_asset", side_effect=download):
            r = self.client.post("/v1/downloads", json={"text": URL}, headers=self.headers)
            self.assertEqual(r.status_code, 202)
            job_id = r.json()["data"]["id"]
            for _ in range(100):
                job = self.client.get(f"/v1/downloads/{job_id}", headers=self.headers).json()["data"]
                if job["state"] in ("done", "error"):
                    break
                time.sleep(.01)
        self.assertEqual(job["state"], "done")
        path = job["files"][0]["download_url"]
        self.assertEqual(self.client.get(path, headers=self.headers).content, b"test-media")
        self.assertEqual(self.client.get(f"/v1/downloads/{job_id}/files/job.json", headers=self.headers).status_code, 404)
        saved = (self.root / "data" / job_id / "job.json").read_text()
        self.assertNotIn("signed", saved)
        self.assertNotIn("test-visitor", saved)
        reopened = create_app(session=self.session, output=self.root / "data", key=KEY)
        with TestClient(reopened) as other:
            self.assertEqual(other.get(path, headers=self.headers).content, b"test-media")

    def test_restart_marks_unfinished_job(self):
        folder = self.root / "restart" / "unfinished"
        folder.mkdir(parents=True)
        (folder / "job.json").write_text(json.dumps({"id": "unfinished", "state": "downloading", "files": []}))
        reopened = create_app(session=self.session, output=folder.parent, key=KEY)
        with TestClient(reopened) as other:
            job = other.get("/v1/downloads/unfinished", headers=self.headers).json()["data"]
            self.assertEqual(job["error"]["code"], "INTERRUPTED")

    def test_queue_has_bounded_admission(self):
        runtime = self.app.state.runtime
        with patch.object(runtime.executor, "submit"):
            for _ in range(3):
                self.assertEqual(self.client.post("/v1/downloads", json={"text": URL}, headers=self.headers).status_code, 202)
            r = self.client.post("/v1/downloads", json={"text": URL}, headers=self.headers)
            self.assertEqual(r.status_code, 429)
            self.assertEqual(r.json()["error"]["code"], "QUEUE_FULL")

    def test_partial_gallery_is_not_reported_as_done(self):
        result = dict(RESULT, type="图集", assets=[{"kind": "image", "urls": ["https://p.douyinpic.com/1"]},
                                                   {"kind": "image", "urls": ["https://p.douyinpic.com/2"]}])
        runtime = self.app.state.runtime
        runtime.parser = lambda *args: (result, SESSION)
        def download(asset, folder, index, *args):
            if index == 2:
                raise ExtractError("下载失败")
            (folder / "001.jpg").write_bytes(b"image")
            return {"name": "001.jpg", "kind": "image", "size": 5}
        with patch("api.download_asset", side_effect=download):
            job_id = runtime.enqueue(URL)["id"]
            for _ in range(100):
                job = runtime.job(job_id)
                if job["state"] not in {"queued", "parsing", "downloading"}:
                    break
                time.sleep(.01)
        self.assertEqual(job["state"], "partial")
        self.assertEqual(len(job["files"]), 1)
        self.assertEqual(job["failures"][0]["index"], 2)
