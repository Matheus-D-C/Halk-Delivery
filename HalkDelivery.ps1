# iniciar-tudo.ps1 - sobe os 4 servicos escondidos e mostra o status neste terminal.
# (so ASCII de proposito: o Windows PowerShell 5.1 le acentos errado sem BOM)

$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$logs = Join-Path $raiz 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

# Porta do pagamento: tem que ser a mesma que o front usa (linha "pagamento:" do HTML)
$PORTA_PAGAMENTO = 8003

# ---------- senha do MySQL (digitada escondida) ----------
$sec  = Read-Host 'Senha do MySQL' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
$env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

# ---------- servicos ----------
$servicos = @(
  @{ Nome='cardapio';   Porta=8001;             Banco='delivery_cardapio';  Dir='Delivery-python\cardapio_service';  Exe='..\.venv\Scripts\python.exe'; Args='main.py' },
  @{ Nome='carrinho'; Porta=8002; Banco='delivery_carrinho'; Dir='Carrinho'; Exe='cmd.exe'; Args='/c gradlew.bat bootRun' },
  @{ Nome='pedido';   Porta=8004; Banco='delivery_pedido';   Dir='Pedido';   Exe='cmd.exe'; Args='/c gradlew.bat bootRun' },
  @{ Nome='pagamento';  Porta=$PORTA_PAGAMENTO; Banco='delivery_pagamento'; Dir='Delivery-python\pagamento_service'; Exe='..\.venv\Scripts\python.exe'; Args='main.py' },
  @{ Nome='avaliacoes'; Porta=8005;             Banco='delivery_avaliacao'; Dir='';                                   Exe='node';                     Args='AvaliacaoController.js' }
)

function PortaAberta([int]$porta) {
  $c = New-Object System.Net.Sockets.TcpClient
  try {
    $ar = $c.BeginConnect('127.0.0.1', $porta, $null, $null)
    return ($ar.AsyncWaitHandle.WaitOne(300) -and $c.Connected)
  } catch {
    return $false
  } finally {
    $c.Close()
  }
}

function UltimaLinha($nome) {
  foreach ($suf in @('.err.log', '.log')) {
    $f = Join-Path $logs ($nome + $suf)
    if (Test-Path $f) {
      try { $l = Get-Content $f -Tail 1 -ErrorAction Stop } catch { $l = $null }
      if ($l) { return ([string]$l).Trim() }
    }
  }
  return ''
}

function Iniciar($s) {
  $dir = $raiz
  if ($s.Dir) { $dir = Join-Path $raiz $s.Dir }
  $exe = $s.Exe
  if ($exe -like '*\*') { $exe = Join-Path $dir $exe }
  if ($exe -like '*\*' -and -not (Test-Path $exe)) { throw ('nao achei ' + $s.Exe) }
  $env:PORT    = [string]$s.Porta
  $env:PYTHONUNBUFFERED = '1'   # o log do Python aparece na hora
  $env:DB_NAME = $s.Banco
  return Start-Process -FilePath $exe -ArgumentList $s.Args -WorkingDirectory $dir `
    -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $logs ($s.Nome + '.log')) `
    -RedirectStandardError  (Join-Path $logs ($s.Nome + '.err.log'))
}

function Parar {
  foreach ($s in $servicos) {
    if ($s.Proc -and -not $s.Proc.HasExited) { & taskkill /PID $s.Proc.Id /T /F *> $null }
  }
  # garantia: quem ainda estiver ouvindo nas portas
  foreach ($s in $servicos) {
    Get-NetTCPConnection -LocalPort $s.Porta -State Listen -ErrorAction SilentlyContinue |
      ForEach-Object { & taskkill /PID $_.OwningProcess /T /F *> $null }
  }
}

# ---------- inicia ----------
foreach ($s in $servicos) {
  $s.Proc = $null; $s.Erro = $null; $s.JaRodava = $false
  if (PortaAberta $s.Porta) { $s.JaRodava = $true; continue }   # ja tem algo nessa porta
  try { $s.Proc = Iniciar $s } catch { $s.Erro = $_.Exception.Message }
}

# ---------- painel de status ----------
Clear-Host
$abriu = $false
$fim = 5 + $servicos.Count
try {
  while ($true) {
    [Console]::SetCursorPosition(0, 0)
    Write-Host ('DELIVERY - status dos servicos'.PadRight(78))
    Write-Host ('-' * 78)
    foreach ($s in $servicos) {
      $up = PortaAberta $s.Porta
      if ($up -and $s.JaRodava)                          { $st = 'ONLINE (ja estava rodando)'; $cor = 'Green' }
      elseif ($up)                                       { $st = 'ONLINE';                     $cor = 'Green' }
      elseif ($s.Erro)                                   { $st = 'FALHOU: ' + $s.Erro;         $cor = 'Red' }
      elseif ($s.Proc -and -not $s.Proc.HasExited)       { $st = 'iniciando... ' + (UltimaLinha $s.Nome); $cor = 'Yellow' }
      else                                               { $st = 'OFFLINE ' + (UltimaLinha $s.Nome); $cor = 'Red' }
      Write-Host ('  {0,-11} porta {1}   ' -f $s.Nome, $s.Porta) -NoNewline
      Write-Host ($st.PadRight(50).Substring(0, 50)) -ForegroundColor $cor
      if ($s.Nome -eq 'pedido' -and $up -and -not $abriu -and (PortaAberta 8002)) {
        $abriu = $true
        $html = Get-ChildItem -Path $raiz -Filter 'Delivery*Frontend*.html' | Select-Object -First 1
        if ($html) { Start-Process $html.FullName }
      }
    }
    Write-Host ('-' * 78)
    Write-Host ('Pressione Q para desligar tudo. Logs na pasta logs\'.PadRight(78))
    if ([Console]::KeyAvailable) {
      $k = [Console]::ReadKey($true)
      if ($k.Key -eq 'Q') { break }
    }
    Start-Sleep -Milliseconds 1500
  }
} finally {
  [Console]::SetCursorPosition(0, $fim)
  Write-Host 'Desligando os servicos...'
  Parar
  Write-Host 'Tudo desligado.'
}