. (Join-Path $PSScriptRoot 'common.ps1')
if (-not (Test-Path -LiteralPath '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Warning 'Created .env. Review its local-only credentials and ports.'
}
Invoke-Compose up --build -d
Invoke-Compose ps

