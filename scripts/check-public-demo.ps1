#requires -Version 5.1
<#
.SYNOPSIS
  Comprova que apenas o rastreamento publico esta exposto no gateway 8090.
#>
[CmdletBinding()]
param([ValidateRange(1024,65535)] [int]$GatewayPort = 8090)
$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:$GatewayPort"

function HttpStatus([string]$uri) {
    try {
        $res = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        return [int]$res.StatusCode
    } catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw "Gateway indisponivel: $($_.Exception.Message)"
    }
}

$good = HttpStatus "$base/tracking.html"
if ($good -ne 200) { throw "Pagina publica: esperado 200, obtido $good" }

$denied = @("/index.html", "/api/products", "/api/units", "/api/shipments", "/label.html", "/scanner.html", "/assets/app.js")
foreach ($path in $denied) {
    $status = HttpStatus "$base$path"
    if ($status -ne 404) {
        throw "BLOQUEIO FALHOU em $path: HTTP $status. NAO INICIE O TUNEL."
    }
    Write-Host "Bloqueado (404): $path" -ForegroundColor Green
}
Write-Host "A pagina publica (200) esta acessivel e as rotas operacionais foram bloqueadas." -ForegroundColor Green
Write-Host "Verifique a API real usando um codigo RT- valido quando houver volume cadastrado." -ForegroundColor Cyan
