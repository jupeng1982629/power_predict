@echo off
setlocal

set "DOCKER_BIN=C:\Program Files\Docker\Docker\resources\bin"
if exist "%DOCKER_BIN%\docker.exe" (
  set "PATH=%DOCKER_BIN%;%PATH%"
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0init-postgres-schema.ps1" %*
exit /b %ERRORLEVEL%
