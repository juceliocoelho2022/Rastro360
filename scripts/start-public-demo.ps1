#requires -Version 5.1
<#
.SYNOPSIS
  Abre SOMENTE as rotas publicas do Rastro360 em 127.0.0.1:8090.
.DESCRIPTION
  Execute em outra janela do PowerShell, com Spring Boot rodando na porta 8081.
  Nunca conecte o tunel Cloudflare diretamente na porta 8081.
.EXAMPLE
  .\scripts\start-public-demo.ps1
#>
[CmdletBinding()]
param(
    [ValidateRange(1024,65535)] [int]$BackendPort = 8081,
    [ValidateRange(1024,65535)] [int]$GatewayPort = 8090
)
$ErrorActionPreference = "Stop"
if ($BackendPort -eq $GatewayPort) {
    throw "O gateway publico nao pode usar a porta privada do Spring Boot."
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$classFile = Join-Path $projectRoot "target\classes\br\com\rastro360\PublicTrackingProxy.class"
if (-not (Test-Path -LiteralPath $classFile)) {
    throw "Classe do gateway nao encontrada. Execute 'mvn clean verify' na raiz do projeto."
}
try {
    $health = Invoke-WebRequest -Uri "http://127.0.0.1:$BackendPort/tracking.html" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
    if ($health.StatusCode -ne 200) { throw "O backend nao respondeu HTTP 200." }
} catch {
    throw "Spring Boot indisponivel em 127.0.0.1:$BackendPort. Inicie-o primeiro. Detalhes: $($_.Exception.Message)"
}

$env:RASTRO360_PRIVATE_PORT = [string]$BackendPort
$env:RASTRO360_PUBLIC_PORT = [string]$GatewayPort

Write-Host "PORTAL PUBLICO: http://127.0.0.1:$GatewayPort/tracking.html" -ForegroundColor Green
Write-Host "SPRING BOOT:    http://127.0.0.1:$BackendPort (NAO publicar)" -ForegroundColor Yellow
Write-Host "Mantenha ESTA janela aberta. Em outra, rode:" -ForegroundColor Cyan
Write-Host "cloudflared tunnel --url http://127.0.0.1:$GatewayPort"
Write-Host "Para desligar o gateway, pressione Ctrl+C."
& java -cp (Join-Path $projectRoot "target\classes") br.com.rastro360.PublicTrackingProxy
if ($LASTEXITCODE -ne 0) { throw "O gateway terminou com erro ($LASTEXITCODE)." }
