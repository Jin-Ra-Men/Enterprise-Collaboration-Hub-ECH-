@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo CSTalk local dev — checks health, starts only what is missing.
echo PostgreSQL must be running. See docs\ENVIRONMENT_SETUP.md
echo.

where curl >nul 2>&1
if errorlevel 1 (
  echo ERROR: curl not found. Install curl or use tools\start-ech-backend.bat and tools\start-ech-realtime.bat manually.
  pause
  exit /b 1
)

set "START_BACKEND=0"
set "START_REALTIME=0"

curl -s -f "http://localhost:8080/api/health" >nul 2>&1
if errorlevel 1 (set "START_BACKEND=1") else (echo [OK] Backend :8080)

curl -s -f "http://localhost:3001/health" >nul 2>&1
if errorlevel 1 (set "START_REALTIME=1") else (echo [OK] Realtime :3001)

if "%START_BACKEND%"=="0" if "%START_REALTIME%"=="0" (
  echo All servers already running. Open http://localhost:8080
  pause
  exit /b 0
)

if "%START_BACKEND%"=="1" (
  echo Starting Backend in a new window...
  start "ECH Backend :8080" /D "%~dp0backend" cmd /k gradlew.bat bootRun
)

if "%START_REALTIME%"=="1" (
  echo Starting Realtime in a new window...
  start "ECH Realtime :3001" cmd /k "cd /d ""%~dp0realtime"" && (if not exist node_modules\ npm.cmd install) && npm.cmd run dev"
)

echo.
echo New windows were opened for any service that was down.
echo When Backend is up: http://localhost:8080
pause
endlocal
