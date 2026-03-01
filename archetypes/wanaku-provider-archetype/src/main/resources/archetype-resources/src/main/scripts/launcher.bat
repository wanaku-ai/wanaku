@echo off

@REM Set
if "%OS%"=="Windows_NT" @setlocal
if "%OS%"=="WINNT" @setlocal

@java -jar quarkus-run.jar %*