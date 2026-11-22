@echo off
echo Parando os servicos...

rem 1) encerra quem esta ouvindo em cada porta
for %%P in (8001 8002 8003 8005) do (
  for /f "tokens=5" %%I in ('netstat -ano ^| findstr /R /C:":%%P .*LISTENING"') do (
    echo Porta %%P - PID %%I
    taskkill /PID %%I /T /F >nul 2>&1
  )
)

rem 2) fecha as janelas abertas pelo iniciar-tudo.bat
for %%T in ("cardapio 8001" "kotlin 8002" "pagamento 8003" "avaliacoes 8005") do (
  taskkill /FI "WINDOWTITLE eq %%~T*" /T /F >nul 2>&1
)

echo Pronto.
pause