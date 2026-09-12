#!/bin/bash
set -e
cd "$(dirname "$0")"
if [ ! -x .venv/bin/python ]; then
  python3 -m venv .venv
fi
.venv/bin/python -m pip install -r requirements-api.txt
if [ ! -f .api-session.json ]; then
  .venv/bin/python -m playwright install chromium
  .venv/bin/python setup_session.py
fi
exec .venv/bin/python api.py
