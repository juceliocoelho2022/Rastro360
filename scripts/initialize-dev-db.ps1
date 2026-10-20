#requires -Version 5.1
<#
.SYNOPSIS
  Cria ou copia explicitamente o banco H2 do Rastro360 para uma pasta fixa.
.EXAMPLE
  .\scripts\initialize-dev-db.ps1 -CreateNew
.EXAMPLE
  .\scripts\initialize-dev-db.ps1 -SourceFile "C:\Projetos\rastro360\Rastro360\data\rastro360.mv.db"
#>
[CmdletBinding(DefaultParameterSetName = "Help")]
param(
    [Parameter(Mandatory = $true, ParameterSetName = "New")]
    [switch]$CreateNew,
    [Parameter(Mandatory = $true, ParameterSetName = "Copy")]
    [ValidateNotNullOrEmpty()]
    [string]$SourceFile
)

$ErrorActionPreference = "Stop"

$databaseBase = if ($env:RASTRO360_DB_PATH) {
    $env:RASTRO360_DB_PATH
} else {
    Join-Path $HOME ".rastro360\data\rastro360"
}

if ($databaseBase.EndsWith(".mv.db", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "RASTRO360_DB_PATH deve ser o caminho SEM a extensao .mv.db."
}
$databaseBase = [System.IO.Path]::GetFullPath($databaseBase)
$destination = "$databaseBase.mv.db"

if (Test-Path -LiteralPath $destination) {
    throw "O banco de destino ja existe. NADA FOI ALTERADO: $destination"
}
$parent = Split-Path -Parent $destination
New-Item -Path $parent -ItemType Directory -Force | Out-Null

if ($PSCmdlet.ParameterSetName -eq "Copy") {
    $source = (Resolve-Path -LiteralPath $SourceFile).ProviderPath
    if (-not $source.EndsWith(".mv.db", [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Informe um arquivo H2 completo terminado em .mv.db."
    }
    Write-Host "Pare o Spring Boot antes de copiar o banco." -ForegroundColor Yellow
    Copy-Item -LiteralPath $source -Destination $destination -ErrorAction Stop
    Write-Host "Banco copiado para: $destination" -ForegroundColor Green
} else {
    $h2Root = Join-Path $HOME ".m2\repository\com\h2database\h2"
    if (-not (Test-Path $h2Root)) {
        throw "H2 nao encontrado. Execute mvn dependency:resolve na raiz do projeto."
    }
    $jar = Get-ChildItem $h2Root -Recurse -Filter "h2-*.jar" -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $jar) { throw "Nenhum h2-*.jar encontrado no cache Maven." }
    $jdbcPath = $databaseBase.Replace('\', '/')
    $jdbcUrl = "jdbc:h2:file:$jdbcPath;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
    & java -cp $jar org.h2.tools.Shell -url $jdbcUrl -user sa -password "" -sql "SELECT 1"
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $destination)) {
        throw "Nao foi possivel criar o banco H2. Verifique o erro acima."
    }
    Write-Host "Novo banco H2 criado: $destination" -ForegroundColor Green
}

Write-Host "Use sempre o mesmo RASTRO360_DB_PATH (ou a pasta fixa HOME)." -ForegroundColor Cyan
Write-Host "Os arquivos legados permanecem intactos." -ForegroundColor Cyan
