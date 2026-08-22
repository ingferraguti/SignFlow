param([string]$Grep)

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
 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4',
 'cccccccc-cccc-cccc-cccc-ccccccccccc3');
update reports set state='APPROVED', workflow_version=0, signed_at=null,
 signature_kind=null, signature_artifact_notice=null
where id in (
 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2',
 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4');
update reports set state='MISSING_SIGNER', workflow_version=0, assigned_signer_id=null
where id='cccccccc-cccc-cccc-cccc-ccccccccccc3';
commit;
"@
    $composeArguments = @(
        'exec', '-T', 'postgres', 'psql',
        "--username=$username", "--dbname=$database", '--set=ON_ERROR_STOP=1', "--command=$sql"
    )
    Invoke-Compose @composeArguments
}

function Reset-MvpReleaseFlow {
    $database = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'signflow' }
    $username = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'signflow' }
    $bucket = if ($env:SIGNFLOW_OBJECT_STORAGE_BUCKET) { $env:SIGNFLOW_OBJECT_STORAGE_BUCKET } else { 'signflow-clinical-documents' }
    $minioAlias = Initialize-MinioClientAlias
    $keyQuery = @"
select raw_object_key from hl7_messages where control_id='MVP-E2E-ORU-001' and raw_object_key is not null
union
select d.object_key from clinical_documents d join reports r on r.id=d.report_id
where r.internal_identifier='HL7-LIS-DEMO-MVP-E2E-ORU-001'
union
select er.object_key from external_delivery_receipts er
join external_delivery_operations eo on eo.id=er.operation_id
join reports r on r.id=eo.report_id
where r.internal_identifier='HL7-LIS-DEMO-MVP-E2E-ORU-001';
"@
    $objectKeys = @(Invoke-Compose exec -T postgres psql "--username=$username" "--dbname=$database" `
        '--tuples-only' '--no-align' "--command=$keyQuery") | ForEach-Object { $_.Trim() } | Where-Object { $_ }
    $sql = @"
begin;
create temporary table mvp_reports on commit drop as
select id,practice_id,patient_metadata_id from reports
where internal_identifier='HL7-LIS-DEMO-MVP-E2E-ORU-001';
create temporary table mvp_operations on commit drop as
select id from external_delivery_operations where report_id in (select id from mvp_reports);
delete from external_delivery_commands where operation_id in (select id from mvp_operations);
delete from external_delivery_receipts where operation_id in (select id from mvp_operations);
delete from external_delivery_attempts where operation_id in (select id from mvp_operations);
delete from external_delivery_operations where id in (select id from mvp_operations);
create temporary table mvp_batches on commit drop as
select distinct b.id,b.provider_session_id from signature_batches b
join signature_attempts a on a.batch_id=b.id where a.report_id in (select id from mvp_reports);
delete from signature_batch_operations where batch_id in (select id from mvp_batches);
delete from signature_attempts where batch_id in (select id from mvp_batches);
delete from signature_batches where id in (select id from mvp_batches);
delete from provider_sessions where id in (select provider_session_id from mvp_batches where provider_session_id is not null);
delete from mock_external_delivery_scenarios where report_id in (select id from mvp_reports);
delete from mock_signature_scenarios where report_id in (select id from mvp_reports);
delete from report_review_decisions where report_id in (select id from mvp_reports);
delete from report_workflow_events where report_id in (select id from mvp_reports);
update hl7_messages set duplicate_of_id=null where duplicate_of_id in (
    select id from hl7_messages where control_id='MVP-E2E-ORU-001');
delete from hl7_messages where control_id='MVP-E2E-ORU-001';
delete from clinical_documents where report_id in (select id from mvp_reports);
delete from reports where id in (select id from mvp_reports);
delete from practices where practice_identifier='CASE-EP-MVP-E2E-001'
  and not exists (select 1 from reports where practice_id=practices.id);
delete from patient_metadata where patient_identifier='PATIENT-PAT-MVP-E2E-001'
  and not exists (select 1 from reports where patient_metadata_id=patient_metadata.id);
commit;
"@
    Invoke-Compose exec -T postgres psql "--username=$username" "--dbname=$database" `
        '--set=ON_ERROR_STOP=1' "--command=$sql"
    foreach ($key in $objectKeys) {
        Invoke-Compose exec -T minio mc rm --force "$minioAlias/$bucket/$key"
    }
}

Reset-MvpReleaseFlow
Reset-MockSignatureDemo
Push-Location frontend
try {
    if ($Grep) { & $npm run e2e -- --grep $Grep }
    else { & $npm run e2e }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
    Reset-MockSignatureDemo
    Reset-MvpReleaseFlow
}
