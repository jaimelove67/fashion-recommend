#!/bin/bash
cd "$(dirname "$0")" || exit 1
if [ ! -x .venv/bin/python ]; then
  command -v python3 >/dev/null || { echo '请先安装 Python 3.10 或更新版本'; read -r; exit 1; }
  python3 -m venv .venv || { read -r; exit 1; }
  .venv/bin/python -m pip install -r requirements.txt || { read -r; exit 1; }
fi
.venv/bin/python -m pip show playwright >/dev/null 2>&1 || .venv/bin/python -m pip install -r requirements.txt || exit 1
.venv/bin/python -m playwright install chromium --no-shell || { read -r; exit 1; }
.venv/bin/python app.py
read -r -p '程序已退出，按回车关闭。'
