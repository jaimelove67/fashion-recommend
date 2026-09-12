@echo off
cd /d "%~dp0"
if not exist .venv\Scripts\python.exe python -m venv .venv
if errorlevel 1 goto :failed
.venv\Scripts\python.exe -m pip install -r requirements-api.txt
if errorlevel 1 goto :failed
if not exist .api-session.json (
  .venv\Scripts\python.exe -m playwright install chromium
  if errorlevel 1 goto :failed
  .venv\Scripts\python.exe setup_session.py
  if errorlevel 1 goto :failed
)
.venv\Scripts\python.exe api.py
if errorlevel 1 goto :failed
exit /b 0
:failed
echo API startup failed. See the error above.
pause
exit /b 1
