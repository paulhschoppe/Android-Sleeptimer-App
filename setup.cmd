@echo off
rem Einmalige Einrichtung (JDK + Android SDK) - siehe setup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" %*
pause
