. (Join-Path $PSScriptRoot 'common.ps1')
& (Join-Path $PSScriptRoot 'test-backend.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot 'test-frontend.ps1')

