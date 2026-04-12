@echo off
setlocal EnableExtensions
cd /d "%~dp0..\realtime"
echo [CSTalk] Realtime Node (default http://localhost:3001)
echo Close this window to stop the realtime server.
if not exist "node_modules\" (
  echo Running npm install...
  call npm.cmd install
  if errorlevel 1 exit /b 1
)
call npm.cmd run dev
endlocal
