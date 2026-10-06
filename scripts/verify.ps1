param([switch]$MySql, [switch]$Rabbit)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Invoke-Check {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Verification failed: $Command (exit $LASTEXITCODE)" }
}

Push-Location $projectRoot
try {
    Invoke-Check 'mvn' @('-s', '.mvn/settings.xml', 'clean', 'test', 'spotless:check')
    Invoke-Check 'mvn' @('-s', '.mvn/settings.xml', '-DskipTests', 'package')
    Invoke-Check 'docker' @('compose', 'config', '--quiet')
    if ($MySql) { Invoke-Check 'mvn' @('-s', '.mvn/settings.xml', '-Pmysql-it', 'verify') }
    if ($Rabbit) { Invoke-Check 'mvn' @('-s', '.mvn/settings.xml', '-Prabbit-it', 'verify') }
    Push-Location (Join-Path $projectRoot 'frontend')
    try {
        if (-not (Test-Path -LiteralPath 'node_modules')) { Invoke-Check 'npm' @('ci') }
        foreach ($check in @('test', 'typecheck', 'lint', 'format:check', 'build')) {
            Invoke-Check 'npm' @('run', $check)
        }
    } finally { Pop-Location }
    Write-Output 'All selected checks passed. External model and browser acceptance are separate checks.'
} finally { Pop-Location }
