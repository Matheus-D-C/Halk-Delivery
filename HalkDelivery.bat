@echo off
set "DB_PASSWORD="
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$s=Read-Host 'Senha do MySQL' -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))"`) do set "DB_PASSWORD=%%P"

start "python 8001+8003" cmd /k "cd /d "%~dp0Delivery-python" && .venv\Scripts\python.exe rodar_python.py"
start "kotlin 8002"     cmd /k "cd /d "%~dp0Delivery-kotlin" && set "DB_NAME=delivery_carrinho" && gradlew.bat bootRun"
start "avaliacoes 8005" cmd /k "cd /d "%~dp0" && set "PORT=8005" && set "DB_NAME=delivery_avaliacao" && node AvaliacaoController.js"

echo Aguardando o Kotlin (porta 8002)...
powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; $fim=(Get-Date).AddSeconds(90); while((Get-Date) -lt $fim){ try{ $c=New-Object Net.Sockets.TcpClient; $c.Connect('127.0.0.1',8002); $c.Close(); break }catch{ Start-Sleep 2 } }"

rem abre a pagina web no navegador padrao
for %%F in ("%~dp0Delivery*Frontend*.html") do (
  start "" "%%F"
  goto :aberto
)
:aberto