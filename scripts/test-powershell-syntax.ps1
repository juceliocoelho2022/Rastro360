#requires -Version 5.1
<#
.SYNOPSIS
  Verifica a sintaxe de todos os scripts PowerShell do projeto.
.DESCRIPTION
  Usa o parser do PowerShell sem executar os scripts nem iniciar serviços.
  Pode ser executado no Windows e no GitHub Actions (pwsh).
#>
$ErrorActionPreference = "Stop"

$files = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter "*.ps1" -File)
if ($files.Count -eq 0) {
    throw "Nenhum script PowerShell encontrado para verificacao."
}

$failed = $false
foreach ($file in $files) {
    $tokens = $null
    $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile(
        $file.FullName, [ref]$tokens, [ref]$parseErrors
    )

    if (@($parseErrors).Count -gt 0) {
        $failed = $true
        foreach ($parseError in $parseErrors) {
            Write-Host ("ERRO {0}:{1} - {2}" -f
                $file.Name, $parseError.Extent.StartLineNumber,
                $parseError.Message) -ForegroundColor Red
        }
    } else {
        Write-Host "SINTAXE OK: $($file.Name)" -ForegroundColor Green
    }
}

if ($failed) {
    throw "Falha de sintaxe detectada em scripts PowerShell."
}

Write-Host "Todos os $($files.Count) scripts PowerShell passaram no parser." -ForegroundColor Green
