. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-JavaEnvironment
$maven = Get-MavenExecutable
$dockerDirectory = Split-Path (Get-DockerExecutable)
$env:PATH = "$dockerDirectory;$env:PATH"
Push-Location backend
try {
    & $maven test
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Pop-Location }

