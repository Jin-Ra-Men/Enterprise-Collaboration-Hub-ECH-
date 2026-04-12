@echo off
setlocal EnableExtensions
cd /d "%~dp0..\backend"
echo [CSTalk] Spring Boot (default http://localhost:8080)
echo Close this window to stop the backend.
gradlew.bat bootRun
endlocal
