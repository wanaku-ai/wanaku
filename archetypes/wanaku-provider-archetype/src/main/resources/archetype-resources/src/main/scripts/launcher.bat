@echo off

set SDM_HOME=%~dp0\..

@REM Set
if %OS%=="Windows_NT" @setlocal
if %OS%=="WINNT" @setlocal

@java -jar quarkus-run.jar %*