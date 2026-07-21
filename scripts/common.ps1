Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $script:ProjectRoot

function Get-DockerExecutable {
    $command = Get-Command docker -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $installed = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
    if (Test-Path -LiteralPath $installed) { return $installed }
    throw 'Docker is required. Install and start Docker Desktop.'
}

function Get-ComposeArguments {
    $arguments = @('compose', '-f', 'compose.yaml')
    if (Test-Path -LiteralPath '.local\compose.tls.yaml') {
        $arguments += @('-f', '.local\compose.tls.yaml')
    }
    return $arguments
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $docker = Get-DockerExecutable
    $composeArguments = Get-ComposeArguments
    & $docker @composeArguments @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed with exit code $LASTEXITCODE." }
}

function Initialize-JavaEnvironment {
    if (-not $env:JAVA_HOME) {
        $jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    }
    if (-not $env:JAVA_HOME) { throw 'JDK 21 is required.' }
    $env:MAVEN_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'
}

function Get-MavenExecutable {
    $command = Get-Command mvn -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $portable = Get-ChildItem '.tools' -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($portable) { return (Join-Path $portable.FullName 'bin\mvn.cmd') }
    throw 'Maven is required. Install Maven or place a distribution under .tools.'
}

function Get-NpmExecutable {
    $command = Get-Command npm -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $portable = Get-ChildItem '.tools' -Directory -Filter 'node-v*-win-x64' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($portable) {
        $env:PATH = "$($portable.FullName);$env:PATH"
        $env:NODE_USE_SYSTEM_CA = '1'
        return (Join-Path $portable.FullName 'npm.cmd')
    }
    throw 'Node.js and npm are required.'
}

