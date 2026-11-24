@echo off
rem Use se fechou a janela do iniciar-tudo no X e os servicos continuaram rodando.
for %%P in (8001 8002 8003 8004 8005) do (
  for /f "tokens=5" %%I in ('netstat -ano ^| findstr /R /C:":%%P .*LISTENING"') do (
    taskkill /PID %%I /T /F >nul 2>&1
  )
)