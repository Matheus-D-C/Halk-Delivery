@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0HalkDelivery.ps1"
if errorlevel 1 pause
