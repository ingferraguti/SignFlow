param([string]$Destination)

. (Join-Path $PSScriptRoot 'common.ps1')
$database = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'signflow' }
$username = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'signflow' }
$bucket = if ($env:SIGNFLOW_OBJECT_STORAGE_BUCKET) { $env:SIGNFLOW_OBJECT_STORAGE_BUCKET } else { 'signflow-clinical-documents' }
if (-not $Destination) { $Destination = Join-Path $script:ProjectRoot ".local\backups\$(Get-Date -Format 'yyyyMMdd-HHmmss')" }
$destinationPath = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $destinationPath) {
    if ((Get-ChildItem -LiteralPath $destinationPath -Force | Select-Object -First 1)) {
        throw "Backup destination is not empty: $destinationPath"
    }
} else { New-Item -ItemType Directory -Path $destinationPath -Force | Out-Null }
$objectsPath = Join-Path $destinationPath 'object-storage'
New-Item -ItemType Directory -Path $objectsPath -Force | Out-Null
$dumpPath = Join-Path $destinationPath 'postgres.dump'
$token = [guid]::NewGuid().ToString('N')
$containerDump = "/tmp/signflow-$token.dump"
$containerObjects = "/tmp/signflow-$token-objects"

try {
    Invoke-Compose exec -T postgres pg_dump "--username=$username" "--dbname=$database" `
        '--format=custom' '--no-owner' "--file=$containerDump"
    Invoke-Compose cp "postgres:$containerDump" $dumpPath
    Invoke-Compose exec -T minio mkdir --parents $containerObjects
    $minioAlias = Initialize-MinioClientAlias
    Invoke-Compose exec -T minio mc mirror "$minioAlias/$bucket" $containerObjects
    Invoke-Compose cp "minio:$containerObjects/." $objectsPath
} finally {
    Invoke-Compose exec -T postgres rm -f $containerDump
    Invoke-Compose exec -T minio rm --recursive --force $containerObjects
}

$objectFiles = @(Get-ChildItem -LiteralPath $objectsPath -File -Recurse)
$manifest = [ordered]@{
    formatVersion = 1
    applicationVersion = if ($env:SIGNFLOW_VERSION) { $env:SIGNFLOW_VERSION } else { '0.1.0' }
    createdAt = (Get-Date).ToUniversalTime().ToString('o')
    database = $database
    bucket = $bucket
    postgresSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $dumpPath).Hash.ToLowerInvariant()
    objectCount = $objectFiles.Count
    fictionalLocalDataset = $true
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $destinationPath 'manifest.json') -Encoding utf8
Write-Host "Local backup completed: $destinationPath"
