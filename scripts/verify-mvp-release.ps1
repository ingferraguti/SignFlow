param(
    [int]$FrontendPort = 3300,
    [int]$BackendPort = 18081,
    [int]$KeycloakPort = 18082,
    [int]$PostgresPort = 55432,
    [int]$MinioPort = 19000,
    [int]$MinioConsolePort = 19001,
    [int]$MllpPort = 12575,
    [switch]$KeepStack
)

. (Join-Path $PSScriptRoot 'common.ps1')
$releaseVersion = '0.1.0'
$projectName = 'signflow-mvp-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
$releaseRoot = [IO.Path]::GetFullPath((Join-Path $script:ProjectRoot ".local\release\$projectName"))
$allowedReleaseRoot = [IO.Path]::GetFullPath((Join-Path $script:ProjectRoot '.local\release')) + [IO.Path]::DirectorySeparatorChar
$evidenceRoot = Join-Path $script:ProjectRoot '.local\release-evidence'
$backupRoot = Join-Path $releaseRoot 'backup'
$realmPath = Join-Path $releaseRoot 'signflow-realm.json'

foreach ($port in @($FrontendPort, $BackendPort, $KeycloakPort, $PostgresPort, $MinioPort, $MinioConsolePort, $MllpPort)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Port $port is already in use. Choose another release-test port."
    }
}
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
$realm = Get-Content -LiteralPath (Join-Path $script:ProjectRoot 'infra\keycloak\signflow-realm.json') -Raw | ConvertFrom-Json
$frontendUrl = "http://localhost:$FrontendPort"
$backendUrl = "http://localhost:$BackendPort"
$keycloakUrl = "http://localhost:$KeycloakPort"
$minioUrl = "http://localhost:$MinioPort"
$realm.clients[0].redirectUris = @("$frontendUrl/api/auth/callback/keycloak")
$realm.clients[0].webOrigins = @($frontendUrl)
$realm.clients[0].attributes.'post.logout.redirect.uris' = "$frontendUrl/login"
$realm | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $realmPath -Encoding utf8

$env:COMPOSE_PROJECT_NAME = $projectName
$env:FRONTEND_PORT = [string]$FrontendPort
$env:BACKEND_PORT = [string]$BackendPort
$env:KEYCLOAK_PORT = [string]$KeycloakPort
$env:POSTGRES_PORT = [string]$PostgresPort
$env:MINIO_PORT = [string]$MinioPort
$env:MINIO_CONSOLE_PORT = [string]$MinioConsolePort
$env:MLLP_PORT = [string]$MllpPort
$env:NEXT_PUBLIC_BACKEND_URL = $backendUrl
$env:SIGNFLOW_OBJECT_STORAGE_PUBLIC_ENDPOINT = $minioUrl
$env:SIGNFLOW_CORS_ALLOWED_ORIGINS = $frontendUrl
$env:SIGNFLOW_OIDC_ISSUER_URI = "$keycloakUrl/realms/signflow"
$env:NEXTAUTH_URL = $frontendUrl
$env:KEYCLOAK_EXTERNAL_ISSUER = "$keycloakUrl/realms/signflow"
$env:NEXT_PUBLIC_KEYCLOAK_EXTERNAL_ISSUER = "$keycloakUrl/realms/signflow"
$env:KEYCLOAK_REALM_IMPORT = $realmPath
$env:PLAYWRIGHT_BASE_URL = $frontendUrl
$env:SIGNFLOW_VERSION = $releaseVersion

$started = $false
$verified = $false
$startedAt = (Get-Date).ToUniversalTime()
try {
    & (Join-Path $PSScriptRoot 'scan-repository.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & (Join-Path $PSScriptRoot 'test-migrations.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & (Join-Path $PSScriptRoot 'test-all.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & (Join-Path $PSScriptRoot 'scan-dependencies.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Invoke-Compose up --build -d --wait
    $started = $true
    & (Join-Path $PSScriptRoot 'test-health.ps1') -FrontendUrl $frontendUrl -BackendUrl $backendUrl `
        -KeycloakUrl $keycloakUrl -MinioUrl $minioUrl
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & (Join-Path $PSScriptRoot 'test-e2e.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & (Join-Path $PSScriptRoot 'backup-local.ps1') -Destination $backupRoot
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Invoke-Compose exec -T postgres psql '--username=signflow' '--dbname=signflow' '--set=ON_ERROR_STOP=1' `
        "--command=insert into application_metadata(property_key,property_value) values ('mvp.restore.probe','must-disappear');"
    & (Join-Path $PSScriptRoot 'restore-local.ps1') -BackupPath $backupRoot -Force
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & (Join-Path $PSScriptRoot 'test-health.ps1') -FrontendUrl $frontendUrl -BackendUrl $backendUrl `
        -KeycloakUrl $keycloakUrl -MinioUrl $minioUrl
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $probe = Invoke-Compose exec -T postgres psql '--username=signflow' '--dbname=signflow' `
        '--tuples-only' '--no-align' "--command=select count(*) from application_metadata where property_key='mvp.restore.probe';"
    if (($probe | Select-Object -Last 1).Trim() -ne '0') { throw 'Restore verification failed: the post-backup probe survived.' }
    $verified = $true
} finally {
    $finishedAt = (Get-Date).ToUniversalTime()
    $evidence = [ordered]@{
        version = $releaseVersion
        project = $projectName
        startedAt = $startedAt.ToString('o')
        finishedAt = $finishedAt.ToString('o')
        verified = $verified
        migrationTarget = 23
        frontendUrl = $frontendUrl
        isolatedEphemeralStack = $true
        backupRestoreVerified = $verified
    }
    $evidencePath = Join-Path $evidenceRoot "$releaseVersion-$(Get-Date -Format 'yyyyMMdd-HHmmss').json"
    $evidence | ConvertTo-Json | Set-Content -LiteralPath $evidencePath -Encoding utf8
    Write-Host "Release evidence: $evidencePath"
    if ($started -and -not $KeepStack) {
        try { Invoke-Compose down --volumes --remove-orphans } catch { Write-Warning $_ }
    }
    if (-not $KeepStack -and $releaseRoot.StartsWith($allowedReleaseRoot, [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $releaseRoot -Recurse -Force
    }
}
if (-not $verified) { throw 'MVP release verification did not complete.' }
Write-Host 'SignFlow MVP 0.1.0 release verification passed.'
