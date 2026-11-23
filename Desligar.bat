@echo off
for %%P in (8001 8002 8003 8005) do (
  for /f "tokens=5" %%I in ('netstat -ano ^| findstr /R /C:"%%P .*LISTENING*") do (
    taskkill /PID %%I /T /F >nul 2>&1
  )
)
for %%T in ("python 8001+8003" "kotlin 8002" "avaliacoes 8005") do (
  taskkill /FI "WINDOWTITLE eq %%~T*" /T /F >nul 2>&1
)