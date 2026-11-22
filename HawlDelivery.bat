@echo off
if not defined DB_PASSWORD (
  echo Defina a senha antes de iniciar:  set "DB_PASSWORD=sua_senha"
  pause
  exit /b 1
)

start "cardapio 8001"   cmd /k "cd /d "%~dp0Delivery-python\cardapio_service" && set "PORT=8001" && set "DB_NAME=delivery_cardapio" && .venv\Scripts\python.exe cardapio_main.py"
start "kotlin 8002"     cmd /k "cd /d "%~dp0Delivery-kotlin" && set "DB_NAME=delivery_carrinho" && gradlew.bat bootRun"
start "pagamento 8003"  cmd /k "cd /d "%~dp0Delivery-python\pagamento_service" && set "PORT=8003" && set "DB_NAME=delivery_pagamento" && .venv\Scripts\python.exe pagamento_main.py"
start "avaliacoes 8005" cmd /k "cd /d "%~dp0" && set "PORT=8005" && set "DB_NAME=delivery_avaliacao" && node AvaliacaoController.js"

echo Aguardando o Kotlin (porta 8002)...
powershell -NoProfile -Command "$t=0; while(-not (Test-NetConnection localhost -Port 8002 -InformationLevel Quiet) -and $t -lt 90){Start-Sleep 2; $t+=2}"

rem abre a pagina web no navegador padrao
for %%F in ("%~dp0Delivery*Frontend*.html") do (
  start "" "%%F"
  goto :aberto
)
:aberto