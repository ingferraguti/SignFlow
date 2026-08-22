. (Join-Path $PSScriptRoot 'common.ps1')

$repositoryFiles = @(& git ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate repository candidate files.' }
$errors = [System.Collections.Generic.List[string]]::new()

if ($repositoryFiles -contains '.env') { $errors.Add('The local .env file must never be tracked.') }
$forbiddenExtensions = @('.p12', '.pfx', '.jks', '.keystore', '.key')
foreach ($path in $repositoryFiles) {
    if ($forbiddenExtensions -contains [IO.Path]::GetExtension($path).ToLowerInvariant()) {
        $errors.Add("Tracked key/certificate material is forbidden: $path")
    }
}

$textFiles = $repositoryFiles | Where-Object {
    $_ -notmatch '^frontend/package-lock\.json$' -and
    [IO.Path]::GetExtension($_).ToLowerInvariant() -notin @('.png', '.jpg', '.jpeg', '.gif', '.ico', '.pdf')
}
$secretPatterns = [ordered]@{
    'private key material' = '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'
    'AWS access key' = '\bAKIA[0-9A-Z]{16}\b'
    'GitHub token' = '\bgh[pousr]_[A-Za-z0-9_]{20,}\b'
    'OpenAI token' = '\bsk-[A-Za-z0-9]{20,}\b'
    'Slack token' = '\bxox[baprs]-[A-Za-z0-9-]{20,}\b'
}

$allowedFiscalCodes = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
@(
    'DMSLGN80A01H501U', 'DMCREA80A01H501U', 'TSTDAA80A01H501X', 'TSTBRN75B02H501Y',
    'TSTAPR81E05H501Q', 'TSTALT70C03H501Z', 'TSTCRL90C43H501W', 'TSTDRA85D04H501V',
    'TSTCHR85A41H501Q', 'TSTLNE79B02H501R', 'TSTMVP90A01H501Q'
) | ForEach-Object { [void]$allowedFiscalCodes.Add($_) }
$fiscalPattern = [regex]'\b[A-Z]{6}[0-9]{2}[A-Z][0-9]{2}[A-Z][0-9]{3}[A-Z]\b'
$emailPattern = [regex]'\b[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})\b'

foreach ($path in $textFiles) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
    $content = Get-Content -LiteralPath $path -Raw
    if ($null -eq $content) { $content = '' }
    foreach ($entry in $secretPatterns.GetEnumerator()) {
        if ($content -match $entry.Value) { $errors.Add("Possible $($entry.Key) in $path") }
    }
    foreach ($match in $fiscalPattern.Matches($content)) {
        if (-not $allowedFiscalCodes.Contains($match.Value)) {
            $errors.Add("Unapproved fiscal-code-shaped value '$($match.Value)' in $path")
        }
    }
    foreach ($match in $emailPattern.Matches($content)) {
        $domain = $match.Groups[1].Value.ToLowerInvariant()
        if ($domain -notin @('signflow.local', 'signflow.invalid')) {
            $errors.Add("Non-fictional email domain '$domain' in $path")
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | Sort-Object -Unique | ForEach-Object { Write-Error $_ }
    throw "Repository privacy/secret scan failed with $($errors.Count) finding(s)."
}
Write-Host "Repository scan passed: $($repositoryFiles.Count) tracked or untracked non-ignored files, no key material, known token pattern, unapproved fiscal-code-shaped fixture, or non-demo email domain."
