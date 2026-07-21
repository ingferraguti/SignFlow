. (Join-Path $PSScriptRoot 'common.ps1')
$npm = Get-NpmExecutable
Push-Location frontend
try {
    & $npm ci
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $npm run lint
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $env:NEXT_TELEMETRY_DISABLED = '1'
    & $npm run build
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Pop-Location }

