. (Join-Path $PSScriptRoot 'common.ps1')
$npm = Get-NpmExecutable
$docker = Get-DockerExecutable

Push-Location frontend
try {
    & $npm ci
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $npm audit --audit-level=high
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Pop-Location }

$cache = Join-Path $script:ProjectRoot '.tools\trivy-cache'
New-Item -ItemType Directory -Path $cache -Force | Out-Null
$dockerArguments = @(
    'run', '--rm',
    '--mount', "type=bind,source=$script:ProjectRoot,target=/workspace,readonly",
    '--mount', "type=bind,source=$cache,target=/root/.cache"
)
$mavenCache = Join-Path $env:USERPROFILE '.m2'
if (Test-Path -LiteralPath $mavenCache) {
    $dockerArguments += @('--mount', "type=bind,source=$mavenCache,target=/root/.m2,readonly")
}
$localCa = Join-Path $script:ProjectRoot '.local\local-root-ca.crt'
if (Test-Path -LiteralPath $localCa) {
    $dockerArguments += @('--mount', "type=bind,source=$localCa,target=/etc/signflow-local-ca.crt,readonly",
        '--env', 'SSL_CERT_FILE=/etc/signflow-local-ca.crt')
}
$trivyBaseArguments = $dockerArguments + @(
    'aquasec/trivy:0.73.0', 'fs', '--scanners', 'vuln', '--severity', 'HIGH,CRITICAL', '--exit-code', '1'
)

foreach ($manifest in @('/workspace/backend/pom.xml', '/workspace/frontend/package-lock.json')) {
    & $docker @trivyBaseArguments $manifest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
