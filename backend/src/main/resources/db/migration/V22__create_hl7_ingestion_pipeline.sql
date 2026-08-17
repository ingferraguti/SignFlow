create table hl7_messages (
    id uuid primary key,
    direction varchar(10) not null default 'INBOUND',
    transport varchar(10) not null,
    status varchar(20) not null,
    source_system_id uuid references source_systems(id),
    source_system_code varchar(60),
    message_type varchar(12),
    trigger_event varchar(12),
    hl7_version varchar(12),
    control_id varchar(120),
    idempotency_key varchar(160) not null,
    deduplication_key char(64) unique,
    payload_sha256 char(64) not null,
    correlation_id varchar(160) not null,
    raw_object_key varchar(500) unique,
    raw_size_bytes bigint not null,
    raw_retention_until timestamptz not null,
    report_id uuid references reports(id),
    document_id uuid references clinical_documents(id),
    duplicate_of_id uuid references hl7_messages(id),
    cda_builder_called boolean not null default false,
    document_normalizer_called boolean not null default false,
    pdf_a3_converter_called boolean not null default false,
    passthrough_applied boolean not null default false,
    pipeline_steps jsonb not null default '[]'::jsonb,
    error_code varchar(80),
    error_message varchar(500),
    technical_actor varchar(160) not null default 'system.ingestion',
    received_at timestamptz not null default now(),
    processed_at timestamptz,
    constraint chk_hl7_direction check (direction in ('INBOUND', 'OUTBOUND')),
    constraint chk_hl7_transport check (transport in ('REST', 'MLLP')),
    constraint chk_hl7_status check (status in ('RECEIVED', 'PROCESSED', 'DISCARDED')),
    constraint chk_hl7_payload_hash check (payload_sha256 ~ '^[0-9a-f]{64}$'),
    constraint chk_hl7_size check (raw_size_bytes >= 0),
    constraint chk_hl7_pipeline_steps check (jsonb_typeof(pipeline_steps) = 'array'),
    constraint chk_hl7_completion check (
        (status = 'RECEIVED' and processed_at is null)
        or (status in ('PROCESSED', 'DISCARDED') and processed_at is not null)
    )
);

create index idx_hl7_messages_received on hl7_messages(received_at desc, id);
create index idx_hl7_messages_status on hl7_messages(status, received_at desc);
create index idx_hl7_messages_source on hl7_messages(source_system_id, received_at desc);
create index idx_hl7_messages_correlation on hl7_messages(correlation_id);
create index idx_hl7_messages_control on hl7_messages(source_system_id, control_id);
create index idx_hl7_messages_report on hl7_messages(report_id) where report_id is not null;
create index idx_hl7_messages_raw_retention on hl7_messages(raw_retention_until);

create or replace function audit_hl7_message()
returns trigger language plpgsql as $$
declare event_name varchar;
declare event_outcome varchar;
begin
    if tg_op = 'INSERT' then
        event_name := 'HL7_MESSAGE_RECEIVED';
        event_outcome := 'PENDING';
    elsif new.status is distinct from old.status then
        event_name := case when new.status = 'PROCESSED' then 'HL7_MESSAGE_PROCESSED' else 'HL7_MESSAGE_DISCARDED' end;
        event_outcome := case when new.status = 'PROCESSED' then 'SUCCESS' else 'FAILURE' end;
    else
        return new;
    end if;
    perform append_audit_event(event_name, 'TECHNICAL', new.technical_actor,
        new.correlation_id, 'HL7_MESSAGE', new.id::varchar, event_outcome,
        jsonb_strip_nulls(jsonb_build_object(
            'sourceSystemCode', new.source_system_code,
            'messageType', new.message_type,
            'triggerEvent', new.trigger_event,
            'transport', new.transport,
            'reportId', new.report_id,
            'errorCode', new.error_code)),
        new.error_message, coalesce(new.processed_at, new.received_at));
    return new;
end;
$$;

create trigger trg_audit_hl7_message
after insert or update of status on hl7_messages
for each row execute function audit_hl7_message();

-- Ingestion workflow transitions are produced by a technical actor. Replacing the
-- function keeps the append-only trigger introduced in V17 while avoiding the
-- misleading USER classification for machine-driven transitions.
create or replace function audit_report_workflow_event()
returns trigger language plpgsql as $$
declare event_name varchar;
declare workflow_actor_type varchar;
begin
    event_name := case
        when new.operation_type in ('ASSIGN_SIGNER', 'CLEAR_SIGNER') then 'SIGNER_ASSIGNMENT_CHANGED'
        when new.first_preview then 'DOCUMENT_FIRST_PREVIEWED'
        else 'REPORT_STATE_CHANGED'
    end;
    workflow_actor_type := case
        when new.operation_type in ('PARSE_INGESTION', 'COMPLETE_INGESTION') then 'TECHNICAL'
        else 'USER'
    end;
    perform append_audit_event(event_name, workflow_actor_type, new.actor_username, new.operation_key,
        'REPORT', new.report_id::varchar, 'SUCCESS',
        jsonb_build_object('operation', new.operation_type, 'fromState', new.from_state,
            'toState', new.to_state, 'workflowVersion', new.resulting_version,
            'missingFieldCount', case when new.missing_fields = '' then 0 else array_length(string_to_array(new.missing_fields, '|'), 1) end),
        new.reason, new.created_at);
    return new;
end;
$$;

insert into source_systems (
    id, code, company_id, description, active, cda_type, pdf_a3_conversion,
    visible_signature, multiple_signature, send_unsigned, create_cda, passthrough
) values (
    '66666666-6666-6666-6666-666666666662', 'DOC-DEMO',
    '22222222-2222-2222-2222-222222222221', 'Sistema documentale passthrough totalmente fittizio', true,
    'CDA2-REF', false, false, false, false, false, true
);

insert into source_system_fse_document_types (source_system_id, document_type_code, cda_injection_enabled)
values ('66666666-6666-6666-6666-666666666662', 'REF', false);

insert into admin_ui_texts (text_key, text_value) values
('label.ingestionMonitoringTitle', 'Ingestion HL7 e pipeline documentale'),
('button.searchIngestion', 'Cerca messaggi'),
('button.openIngestionMessage', 'Apri dettaglio messaggio'),
('button.closeIngestionMessage', 'Chiudi dettaglio messaggio')
on conflict (text_key) do nothing;
