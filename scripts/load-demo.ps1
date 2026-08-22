. (Join-Path $PSScriptRoot 'common.ps1')
Invoke-Compose up -d --wait postgres keycloak minio backend frontend
$database = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'signflow' }
$username = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'signflow' }
Invoke-Compose exec -T postgres psql "--username=$username" "--dbname=$database" --tuples-only --no-align `
    "--command=select concat('reports=',count(*)) from reports;"
Write-Host 'The fictional demo dataset is migration-backed and application initializers are idempotent.'
