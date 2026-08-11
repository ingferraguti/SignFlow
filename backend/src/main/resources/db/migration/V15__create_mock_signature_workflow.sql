alter table reports
    add column signature_kind varchar(20),
    add column signature_artifact_notice varchar(500),
    add constraint chk_report_signature_kind check (signature_kind is null or signature_kind='MOCK');

update signature_providers
set name='Provider firma MOCK - solo collaudo', base_url=null, credential_reference=null
where code='MOCK-REMOTE';

create table provider_sessions (
    id uuid primary key,
    signature_account_id uuid not null references signature_accounts(id),
    actor_username varchar(160) not null,
    provider_code varchar(60) not null,
    state varchar(20) not null,
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint chk_provider_session_state check (state in ('ACTIVE','CLOSED'))
);

create table signature_batches (
    id uuid primary key,
    signer_username varchar(160) not null,
    signature_account_id uuid not null references signature_accounts(id),
    provider_session_id uuid references provider_sessions(id),
    selection_mode varchar(20) not null,
    filter_snapshot varchar(2000),
    state varchar(30) not null,
    create_operation_key varchar(120) not null,
    create_fingerprint char(64) not null,
    version bigint not null default 0,
    total_count integer not null,
    success_count integer not null default 0,
    failure_count integer not null default 0,
    created_at timestamptz not null default now(),
    confirmed_at timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    cancelled_at timestamptz,
    constraint uq_signature_batch_create unique (signer_username, create_operation_key),
    constraint chk_signature_batch_selection check (selection_mode in ('SINGLE','MANUAL','FILTERED')),
    constraint chk_signature_batch_state check (state in
        ('DRAFT','CONFIRMED','RUNNING','COMPLETED','PARTIAL_SUCCESS','FAILED','CANCELLED')),
    constraint chk_signature_batch_counts check (
        total_count > 0 and success_count >= 0 and failure_count >= 0
        and success_count + failure_count <= total_count)
);

create table signature_attempts (
    id uuid primary key,
    batch_id uuid not null references signature_batches(id),
    report_id uuid not null references reports(id),
    state varchar(20) not null,
    retry_count integer not null default 0,
    max_retries integer not null default 2,
    provider_reference varchar(200),
    artifact_id uuid,
    artifact_name varchar(255),
    artifact_notice varchar(500),
    artifact_content text,
    error_code varchar(80),
    error_message varchar(500),
    started_at timestamptz,
    completed_at timestamptz,
    constraint uq_signature_attempt_report unique (batch_id, report_id),
    constraint uq_signature_attempt_artifact unique (artifact_id),
    constraint chk_signature_attempt_state check (state in
        ('PENDING','SIGNING','SUCCEEDED','FAILED','CANCELLED')),
    constraint chk_signature_attempt_retry check (retry_count >= 0 and retry_count <= max_retries),
    constraint chk_mock_artifact_notice check (
        artifact_id is null or artifact_notice like 'MOCK ONLY%')
);

create table signature_batch_operations (
    id uuid primary key,
    batch_id uuid not null references signature_batches(id),
    attempt_id uuid references signature_attempts(id),
    operation_key varchar(120) not null,
    operation_type varchar(30) not null,
    request_fingerprint char(64) not null,
    created_at timestamptz not null default now(),
    constraint uq_signature_batch_operation unique (batch_id, operation_key)
);

create table mock_signature_scenarios (
    report_id uuid primary key references reports(id),
    failures_before_success integer not null default 0,
    constraint chk_mock_failures check (failures_before_success >= 0)
);

create index idx_provider_sessions_actor on provider_sessions(actor_username, expires_at desc);
create index idx_signature_batches_signer on signature_batches(signer_username, created_at desc);
create index idx_signature_attempts_batch on signature_attempts(batch_id);
create index idx_signature_attempts_report on signature_attempts(report_id);

insert into reports (
    id, internal_identifier, external_identifier, practice_id, patient_metadata_id,
    assigned_signer_id, assigned_approver_id, produced_by, review_separation_required,
    source_system_id, document_type, department, produced_at, modified_at,
    pdf_a3_conversion, visible_signature, multiple_signature, send_unsigned, create_cda,
    passthrough, state, workflow_version, first_previewed_at
) values
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1', 'RPT-MOCK-OK-001', 'MOCK-EXT-OK-001',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
 '55555555-5555-5555-5555-555555555552', '55555555-5555-5555-5555-555555555554',
 'demo.producer', true, '66666666-6666-6666-6666-666666666661', 'PDF-MOCK',
 'Laboratorio fittizio', '2026-08-01T09:00:00+02:00', '2026-08-01T09:15:00+02:00',
 false, false, false, false, false, true, 'APPROVED', 0, '2026-08-01T09:10:00+02:00'),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2', 'RPT-MOCK-RETRY-001', 'MOCK-EXT-RETRY-001',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
 '55555555-5555-5555-5555-555555555552', '55555555-5555-5555-5555-555555555554',
 'demo.producer', true, '66666666-6666-6666-6666-666666666661', 'PDF-MOCK',
 'Laboratorio fittizio', '2026-08-02T09:00:00+02:00', '2026-08-02T09:15:00+02:00',
 false, false, false, false, false, true, 'APPROVED', 0, '2026-08-02T09:10:00+02:00'),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3', 'RPT-MOCK-FAIL-001', 'MOCK-EXT-FAIL-001',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2',
 '55555555-5555-5555-5555-555555555552', '55555555-5555-5555-5555-555555555554',
 'demo.producer', true, '66666666-6666-6666-6666-666666666661', 'PDF-MOCK',
 'Reparto simulato', '2026-08-03T09:00:00+02:00', '2026-08-03T09:15:00+02:00',
 false, false, false, false, false, true, 'APPROVED', 0, '2026-08-03T09:10:00+02:00'),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4', 'RPT-MOCK-OK-002', 'MOCK-EXT-OK-002',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2',
 '55555555-5555-5555-5555-555555555552', '55555555-5555-5555-5555-555555555554',
 'demo.producer', true, '66666666-6666-6666-6666-666666666661', 'PDF-MOCK',
 'Reparto simulato', '2026-08-04T09:00:00+02:00', '2026-08-04T09:15:00+02:00',
 false, false, false, false, false, true, 'APPROVED', 0, '2026-08-04T09:10:00+02:00');

insert into mock_signature_scenarios (report_id, failures_before_success) values
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1', 0),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2', 1),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3', 99),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4', 0);

create or replace function guard_report_workflow_changes()
returns trigger
language plpgsql
as $$
begin
    if (new.state is distinct from old.state
        or new.assigned_signer_id is distinct from old.assigned_signer_id
        or new.assigned_approver_id is distinct from old.assigned_approver_id
        or new.review_separation_required is distinct from old.review_separation_required
        or new.counter_signature_required is distinct from old.counter_signature_required
        or new.counter_signer_id is distinct from old.counter_signer_id
        or new.counter_signature_prepared_at is distinct from old.counter_signature_prepared_at
        or new.signature_kind is distinct from old.signature_kind
        or new.signature_artifact_notice is distinct from old.signature_artifact_notice
        or new.signed_at is distinct from old.signed_at)
       and coalesce(current_setting('signflow.workflow_transition_allowed', true), 'false') <> 'true' then
        raise exception 'Report workflow fields can only be changed by application workflow services'
            using errcode = '42501';
    end if;
    return new;
end;
$$;

insert into admin_ui_texts (text_key, text_value) values
('menu.signatureBatches', 'Firma mock'),
('button.startProviderSession', 'Avvia sessione mock'),
('button.createManualBatch', 'Crea batch dai selezionati'),
('button.signAllFiltered', 'Firma tutti i risultati filtrati'),
('button.confirmBatch', 'Conferma batch'),
('button.startBatch', 'Avvia firma mock'),
('button.cancelBatch', 'Annulla batch'),
('button.retrySignature', 'Riprova firma mock'),
('button.signMockSingle', 'Firma singola mock'),
('button.openSignatureBatch', 'Apri riepilogo batch'),
('button.downloadMockArtifact', 'Scarica attestazione mock');
