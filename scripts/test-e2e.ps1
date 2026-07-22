. (Join-Path $PSScriptRoot 'common.ps1')
$npm = Get-NpmExecutable
Push-Location frontend
try {
    & $npm run e2e
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Pop-Location }
