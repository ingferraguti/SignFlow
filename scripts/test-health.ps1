param(
    [string]$FrontendUrl,
    [string]$BackendUrl,
    [string]$KeycloakUrl,
    [string]$MinioUrl
)

. (Join-Path $PSScriptRoot 'common.ps1')

function Get-PublishedUrl([string]$Service, [int]$ContainerPort) {
    $published = @(Invoke-Compose port $Service $ContainerPort) | Select-Object -Last 1
    if (-not $published -or $published -notmatch ':(\d+)$') {
        throw "Unable to resolve the published port for $Service/$ContainerPort."
    }
    return "http://localhost:$($Matches[1])"
}

if (-not $FrontendUrl) { $FrontendUrl = Get-PublishedUrl 'frontend' 3000 }
if (-not $BackendUrl) { $BackendUrl = Get-PublishedUrl 'backend' 8080 }
if (-not $KeycloakUrl) { $KeycloakUrl = Get-PublishedUrl 'keycloak' 8080 }
if (-not $MinioUrl) { $MinioUrl = Get-PublishedUrl 'minio' 9000 }

function Assert-HttpOk([string]$Name, [string]$Url, [scriptblock]$Validate) {
    $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 15
    if ($response.StatusCode -ne 200) { throw "$Name health check returned HTTP $($response.StatusCode)." }
    if ($Validate -and -not (& $Validate $response)) { throw "$Name health response is invalid." }
    Write-Host "[health] $Name OK - $Url"
}

function Get-ResponseText($Response) {
    if ($Response.Content -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($Response.Content) }
    return [string]$Response.Content
}

Assert-HttpOk 'backend' "$BackendUrl/actuator/health" {
    param($response) ((ConvertFrom-Json (Get-ResponseText $response)).status -eq 'UP')
}
Assert-HttpOk 'frontend' "$FrontendUrl/login" { param($response) (Get-ResponseText $response) -match 'SignFlow' }
Assert-HttpOk 'Keycloak OIDC' "$KeycloakUrl/realms/signflow/.well-known/openid-configuration" {
    param($response) ((ConvertFrom-Json (Get-ResponseText $response)).issuer -eq "$KeycloakUrl/realms/signflow")
}
Assert-HttpOk 'MinIO' "$MinioUrl/minio/health/ready" { param($response) $true }
Invoke-Compose exec -T postgres pg_isready `
    "--username=$(if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'signflow' })" `
    "--dbname=$(if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'signflow' })"
Write-Host '[health] PostgreSQL OK'
