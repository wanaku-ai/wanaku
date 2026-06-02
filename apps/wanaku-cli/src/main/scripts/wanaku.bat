@echo off

setlocal

set INSTALL_DIR=%~dp0

if exist "%INSTALL_DIR%wanaku-cli.exe" (
  "%INSTALL_DIR%wanaku-cli.exe" %*
) else if exist "%INSTALL_DIR%wanaku-cli" (
  "%INSTALL_DIR%wanaku-cli" %*
) else (
  java -jar "%INSTALL_DIR%quarkus-run.jar" %*
)