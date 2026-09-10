import json
import queue
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from unittest.mock import patch

import app


class LocalApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = app.ThreadingHTTPServer(("127.0.0.1", 0), app.Handler)
        cls.base = f"http://127.0.0.1:{cls.server.server_port}"
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)

    def setUp(self):
        app.JOBS.clear()
        while True:
            try:
                app.QUEUE.get_nowait()
                app.QUEUE.task_done()
            except queue.Empty:
                break

    def request(self, path, data=None, **headers):
        request = urllib.request.Request(
            self.base + path,
            data=json.dumps(data).encode() if data is not None else None,
            headers={"X-Local-Token": app.TOKEN, "Content-Type": "application/json", **headers},
        )
        try:
            with urllib.request.urlopen(request, timeout=3) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as response:
            return response.code, response.read()

    def test_page_and_authentication(self):
        status, body = self.request("/")
        self.assertEqual(status, 200)
        self.assertIn(app.TOKEN.encode(), body)
        self.assertEqual(self.request("/api/jobs", **{"X-Local-Token": "invalid"})[0], 403)

    def test_origin_and_host_restrictions(self):
        self.assertEqual(self.request("/api/jobs", Origin="https://example.com")[0], 403)
        self.assertEqual(self.request("/", Host="evil.example")[0], 403)

    def test_create_and_cancel_task_without_starting_browser(self):
        status, body = self.request("/api/jobs", {"text": "分享 https://v.douyin.com/example/"})
        self.assertEqual(status, 202)
        job_id = json.loads(body)["id"]
        self.assertEqual(app.snapshot(job_id)["state"], "queued")
        self.assertEqual(self.request("/api/cancel", {"id": job_id})[0], 200)
        self.assertTrue(app.snapshot(job_id)["cancel"])

    def test_invalid_input_and_queue_limit(self):
        self.assertEqual(self.request("/api/jobs", {"text": "https://example.com"})[0], 400)
        self.assertEqual(self.request("/api/jobs", {"text": 123})[0], 400)
        for _ in range(3):
            self.assertEqual(self.request("/api/jobs", {"text": "https://v.douyin.com/example/"})[0], 202)
        self.assertEqual(self.request("/api/jobs", {"text": "https://v.douyin.com/example/"})[0], 400)

    def test_files_and_path_traversal(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(app, "OUTPUT", Path(directory)):
            output = Path(directory)
            (output / "sample.mp4").write_bytes(b"test-file-content")
            (output / "sample.part").write_bytes(b"incomplete")
            self.assertEqual(self.request("/files/sample.mp4"), (200, b"test-file-content"))
            self.assertEqual(self.request("/files/sample.part")[0], 404)
            self.assertEqual(self.request("/files/%2e%2e/app.py")[0], 404)


if __name__ == "__main__":
    unittest.main()
