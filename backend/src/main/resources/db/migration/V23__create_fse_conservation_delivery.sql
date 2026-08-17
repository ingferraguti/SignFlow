alter table reports drop constraint chk_report_state;
alter table reports add constraint chk_report_state check (state in (
    'RECEIVED', 'PARSED', 'INCOMPLETE', 'MISSING_SIGNER', 'READY_TO_SIGN', 'PREVIEWED',
    'REVIEW_PENDING', 'APPROVED', 'SIGN_BATCH_CREATED', 'SIGNING', 'SIGNED', 'SIGN_ERROR',
    'FSE_VALIDATION_ERROR', 'FSE_SENT', 'FSE_ACCEPTED', 'FSE_REJECTED',
    'CONSERVATION_SENT', 'CONSERVATION_ACCEPTED', 'CONSERVATION_REJECTED'
));

create table external_delivery_operations (
    id uuid primary key,
    report_id uuid not null references reports(id),
    document_id uuid references clinical_documents(id),
    channel varchar(20) not null,
    state varchar(40) not null,
    correlation_id varchar(160) not null,
    adapter_code varchar(80) not null,
    facility_code varchar(80) not null,
    facility_name varchar(200) not null,
    operating_unit varchar(160) not null,
    department varchar(160) not null,
    document_type_code varchar(3) not null references fse_document_types(code),
    remote_reference varchar(200),
    attempt_count integer not null default 0,
    max_retries integer not null default 2,
    version bigint not null default 0,
    error_code varchar(80),
    error_message varchar(500),
    created_by varchar(160) not null,
    created_at timestamptz not null default now(),
    sent_at timestamptz,
    completed_at timestamptz,
    reconciled_at timestamptz,
    updated_at timestamptz not null default now(),
    constraint uq_external_delivery_report_channel unique (report_id, channel),
    constraint uq_external_delivery_correlation unique (correlation_id),
    constraint chk_external_delivery_channel check (channel in ('FSE','CONSERVATION')),
    constraint chk_external_delivery_state check (state in (
        'FSE_VALIDATION_ERROR','FSE_SENT','FSE_ACCEPTED','FSE_REJECTED',
        'CONSERVATION_SENT','CONSERVATION_ACCEPTED','CONSERVATION_REJECTED','TIMEOUT')),
    constraint chk_external_delivery_retry check (attempt_count >= 0 and max_retries between 0 and 10)
);

create table external_delivery_attempts (
    id uuid primary key,
    operation_id uuid not null references external_delivery_operations(id),
    attempt_number integer not null,
    action varchar(20) not null,
    outcome varchar(20) not null,
    correlation_id varchar(160) not null,
    remote_reference varchar(200),
    error_code varchar(80),
    error_message varchar(500),
    started_at timestamptz not null default now(),
    completed_at timestamptz not null default now(),
    constraint uq_external_delivery_attempt unique (operation_id, attempt_number, action),
    constraint chk_external_delivery_action check (action in ('VALIDATE','SEND','RETRY','RECONCILE')),
    constraint chk_external_delivery_outcome check (outcome in ('PENDING','SUCCESS','FAILURE','TIMEOUT'))
);

create table external_delivery_receipts (
    id uuid primary key,
    operation_id uuid not null references external_delivery_operations(id),
    attempt_id uuid not null references external_delivery_attempts(id),
    receipt_type varchar(30) not null,
    object_key varchar(500) not null unique,
    sha256 char(64) not null,
    mime_type varchar(120) not null,
    size_bytes bigint not null,
    created_at timestamptz not null default now(),
    constraint chk_external_receipt_type check (receipt_type in ('SUBMISSION','ACCEPTANCE','REJECTION','TIMEOUT')),
    constraint chk_external_receipt_sha256 check (sha256 ~ '^[0-9a-f]{64}$'),
    constraint chk_external_receipt_size check (size_bytes > 0)
);

create table external_delivery_commands (
    id uuid primary key,
    operation_id uuid not null references external_delivery_operations(id),
    command_key varchar(120) not null,
    command_type varchar(20) not null,
    request_fingerprint char(64) not null,
    resulting_state varchar(40) not null,
    created_at timestamptz not null default now(),
    constraint uq_external_delivery_command unique (operation_id, command_key),
    constraint chk_external_delivery_command check (command_type in ('SEND','RETRY','RECONCILE'))
);

create table mock_external_delivery_scenarios (
    report_id uuid primary key references reports(id),
    fse_rejections_before_acceptance integer not null default 0,
    fse_timeouts_before_result integer not null default 0,
    conservation_rejections_before_acceptance integer not null default 0,
    conservation_timeouts_before_result integer not null default 0,
    constraint chk_mock_external_scenario_counts check (
        fse_rejections_before_acceptance >= 0 and fse_timeouts_before_result >= 0
        and conservation_rejections_before_acceptance >= 0 and conservation_timeouts_before_result >= 0)
);

create index idx_external_delivery_search on external_delivery_operations(channel, state, updated_at desc);
create index idx_external_delivery_report on external_delivery_operations(report_id, created_at desc);
create index idx_external_delivery_attempts on external_delivery_attempts(operation_id, attempt_number desc);
create index idx_external_delivery_receipts on external_delivery_receipts(operation_id, created_at desc);

insert into reports (
    id, internal_identifier, external_identifier, practice_id, patient_metadata_id,
    assigned_signer_id, source_system_id, document_type, department, produced_at, modified_at, signed_at,
    pdf_a3_conversion, visible_signature, multiple_signature, send_unsigned, create_cda, passthrough,
    state, signature_kind, signature_artifact_notice
) values
('ffffffff-ffff-ffff-ffff-fffffffffff1', 'RPT-FSE-MOCK-OK-001', 'FSE-MOCK-EXT-OK-001',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2',
 '55555555-5555-5555-5555-555555555552', '66666666-6666-6666-6666-666666666661',
 'REF', 'Medicina generale', '2026-08-10T09:00:00+02:00', '2026-08-10T09:15:00+02:00', '2026-08-10T09:15:00+02:00',
 false, false, false, false, false, true, 'SIGNED', 'MOCK',
 'MOCK ONLY - documento totalmente fittizio per collaudo FSE e conservazione'),
('ffffffff-ffff-ffff-ffff-fffffffffff2', 'RPT-FSE-MOCK-RETRY-001', 'FSE-MOCK-EXT-RETRY-001',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2',
 '55555555-5555-5555-5555-555555555552', '66666666-6666-6666-6666-666666666661',
 'REF', 'Medicina generale', '2026-08-11T09:00:00+02:00', '2026-08-11T09:15:00+02:00', '2026-08-11T09:15:00+02:00',
 false, false, false, false, false, true, 'SIGNED', 'MOCK',
 'MOCK ONLY - documento totalmente fittizio per collaudo degli esiti negativi');

insert into mock_external_delivery_scenarios
    (report_id, fse_rejections_before_acceptance, conservation_rejections_before_acceptance)
values
('ffffffff-ffff-ffff-ffff-fffffffffff1', 0, 0),
('ffffffff-ffff-ffff-ffff-fffffffffff2', 1, 1);

create or replace function audit_external_delivery_operation()
returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        perform append_audit_event(new.channel || '_DELIVERY_CREATED', 'USER', new.created_by,
            new.correlation_id, 'REPORT', new.report_id::varchar, 'PENDING',
            jsonb_build_object('operationId', new.id, 'documentId', new.document_id,
                'channel', new.channel, 'state', new.state, 'adapterCode', new.adapter_code,
                'facilityCode', new.facility_code), null, new.created_at);
    elsif new.state is distinct from old.state then
        perform append_audit_event(new.channel || '_DELIVERY_STATE_CHANGED', 'TECHNICAL', new.adapter_code,
            new.correlation_id, 'REPORT', new.report_id::varchar,
            case when new.state like '%REJECTED' or new.state like '%ERROR' or new.state='TIMEOUT'
                then 'FAILURE' when new.state like '%SENT' then 'PENDING' else 'SUCCESS' end,
            jsonb_build_object('operationId', new.id, 'documentId', new.document_id,
                'channel', new.channel, 'fromState', old.state, 'toState', new.state,
                'attemptCount', new.attempt_count), new.error_message, now());
    end if;
    return new;
end;
$$;
create trigger trg_audit_external_delivery_operation
after insert or update of state on external_delivery_operations
for each row execute function audit_external_delivery_operation();

create or replace function audit_external_delivery_attempt()
returns trigger language plpgsql as $$
declare operation external_delivery_operations%rowtype;
begin
    select * into operation from external_delivery_operations where id=new.operation_id;
    perform append_audit_event(operation.channel || '_DELIVERY_ATTEMPT', 'TECHNICAL', operation.adapter_code,
        new.correlation_id, 'REPORT', operation.report_id::varchar,
        case when new.outcome='SUCCESS' then 'SUCCESS' when new.outcome='PENDING' then 'PENDING' else 'FAILURE' end,
        jsonb_build_object('operationId', operation.id, 'documentId', operation.document_id,
            'channel', operation.channel, 'action', new.action, 'attemptNumber', new.attempt_number,
            'errorCode', new.error_code), new.error_message, new.completed_at);
    return new;
end;
$$;
create trigger trg_audit_external_delivery_attempt
after insert on external_delivery_attempts
for each row execute function audit_external_delivery_attempt();

create or replace function audit_external_delivery_receipt()
returns trigger language plpgsql as $$
declare operation external_delivery_operations%rowtype;
begin
    select * into operation from external_delivery_operations where id=new.operation_id;
    perform append_audit_event(operation.channel || '_RECEIPT_STORED', 'TECHNICAL', operation.adapter_code,
        operation.correlation_id, 'REPORT', operation.report_id::varchar, 'SUCCESS',
        jsonb_build_object('operationId', operation.id, 'documentId', operation.document_id,
            'receiptId', new.id, 'receiptType', new.receipt_type, 'sizeBytes', new.size_bytes), null, new.created_at);
    return new;
end;
$$;
create trigger trg_audit_external_delivery_receipt
after insert on external_delivery_receipts
for each row execute function audit_external_delivery_receipt();

insert into admin_ui_texts (text_key, text_value) values
('menu.externalDelivery', 'FSE e conservazione'),
('label.externalDeliveryTitle', 'FSE 2.0 e conservazione'),
('button.sendFse', 'Invia a FSE mock'),
('button.sendConservation', 'Invia in conservazione mock'),
('button.reconcileDelivery', 'Riconcilia esito'),
('button.retryDelivery', 'Riprova invio'),
('button.openDeliveryReceipt', 'Scarica ricevuta'),
('button.refreshDeliveries', 'Aggiorna esiti')
on conflict (text_key) do nothing;
