# Third-party source

Source: https://github.com/Evil0ctal/Douyin_TikTok_Download_API

Pinned revision: `3fa19d9aeb5326df3ade80a7ce75f62f7a154dfb` (v5 source, retrieved 2026-09-12).

Author: Evil0ctal and project contributors. License: Apache-2.0 (included in LICENSE).

Files copied from `src/dtk/signing/native/`: abogus.py, sm3.py, websign.py.
Local modification: abogus.py imports sm3 relatively. __init__.py is local.
These utilities generate request parameters; the upstream service, identity pool, and database are not bundled.
