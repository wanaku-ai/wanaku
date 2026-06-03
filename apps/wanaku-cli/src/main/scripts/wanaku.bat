@echo off

if %OS%=="Windows_NT" @setlocal
if %OS%=="WINNT" @setlocal

if exist "%~dp0wanaku-cli.exe" (
    "%~dp0wanaku-cli.exe" %*
    exit /b %ERRORLEVEL%
)

@java -jar "%~dp0..\quarkus-run.jar" %*