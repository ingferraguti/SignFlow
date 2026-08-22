param(
    [Parameter(Mandatory = $true)][string]$BackupPath,
    [switch]$Force
)

. (Join-Path $PSScriptRoot 'common.ps1')
$resolvedBackup = (Resolve-Path -LiteralPath $BackupPath).Path
$manifestPath = Join-Path $resolvedBackup 'manifest.json'
$dumpPath = Join-Path $resolvedBackup 'postgres.dump'
$objectsPath = Join-Path $resolvedBackup 'object-storage'
foreach ($required in @($manifestPath, $dumpPath, $objectsPath)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Incomplete backup: missing $required" }
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.formatVersion -ne 1) { throw "Unsupported backup format: $($manifest.formatVersion)" }
$actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $dumpPath).Hash.ToLowerInvariant()
if ($actualHash -ne $manifest.postgresSha256) { throw 'PostgreSQL backup checksum does not match the manifest.' }
if (-not $Force) {
    $confirmation = Read-Host "This replaces local database '$($manifest.database)' and bucket '$($manifest.bucket)'. Type RESTORE to continue"
    if ($confirmation -ne 'RESTORE') { Write-Host 'Restore cancelled.'; exit 0 }
}

$database = [string]$manifest.database
$username = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'signflow' }
$bucket = [string]$manifest.bucket
$token = [guid]::NewGuid().ToString('N')
$containerDump = "/tmp/signflow-restore-$token.dump"
$containerObjects = "/tmp/signflow-restore-$token-objects"

Invoke-Compose stop frontend backend
try {
    Invoke-Compose cp $dumpPath "postgres:$containerDump"
    Invoke-Compose exec -T postgres dropdb "--username=$username" --if-exists --force $database
    Invoke-Compose exec -T postgres createdb "--username=$username" $database
    Invoke-Compose exec -T postgres pg_restore "--username=$username" "--dbname=$database" `
        '--exit-on-error' '--no-owner' $containerDump

    $minioAlias = Initialize-MinioClientAlias
    Invoke-Compose exec -T minio mc rm --recursive --force "$minioAlias/$bucket"
    Invoke-Compose exec -T minio mc mb --ignore-existing "$minioAlias/$bucket"
    Invoke-Compose exec -T minio mkdir --parents $containerObjects
    Invoke-Compose cp "$objectsPath/." "minio:$containerObjects"
    Invoke-Compose exec -T minio mc mirror $containerObjects "$minioAlias/$bucket"
} finally {
    Invoke-Compose exec -T postgres rm -f $containerDump
    Invoke-Compose exec -T minio rm --recursive --force $containerObjects
    Invoke-Compose up -d --wait backend frontend
}
Write-Host "Local restore completed from: $resolvedBackup"
