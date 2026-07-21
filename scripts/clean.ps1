. (Join-Path $PSScriptRoot 'common.ps1')
Invoke-Compose down --remove-orphans
Write-Host 'Services stopped. Database volume and source files were preserved.'

