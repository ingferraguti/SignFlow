. (Join-Path $PSScriptRoot 'common.ps1')
$confirmation = Read-Host 'This deletes the local SignFlow PostgreSQL volume. Type RESET to continue'
if ($confirmation -ne 'RESET') {
    Write-Host 'Reset cancelled.'
    exit 0
}
Invoke-Compose down --volumes
Invoke-Compose up -d postgres backend
