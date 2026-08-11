. (Join-Path $PSScriptRoot 'common.ps1')
$npm = Get-NpmExecutable

function Reset-MockSignatureDemo {
    $database = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'signflow' }
    $username = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'signflow' }
    $sql = @"
begin;
select set_config('signflow.workflow_transition_allowed','true',true);
delete from signature_batch_operations;
delete from signature_attempts;
delete from signature_batches;
delete from provider_sessions;
delete from report_workflow_events where report_id in (
 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2',
 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4');
update reports set state='APPROVED', workflow_version=0, signed_at=null,
 signature_kind=null, signature_artifact_notice=null
where id in (
 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2',
 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4');
commit;
"@
    $composeArguments = @(
        'exec', '-T', 'postgres', 'psql',
        "--username=$username", "--dbname=$database", '--set=ON_ERROR_STOP=1', "--command=$sql"
    )
    Invoke-Compose @composeArguments
}

Reset-MockSignatureDemo
Push-Location frontend
try {
    & $npm run e2e
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
    Reset-MockSignatureDemo
}
