create table audit_retention_policy (
    id smallint primary key default 1 check (id = 1),
    retention_days integer not null check (retention_days between 30 and 3650),
    updated_by varchar(160) not null,
    updated_at timestamptz not null default now()
);

insert into audit_retention_policy (id, retention_days, updated_by)
values (1, 365, 'system.migration');

create table audit_events (
    id uuid primary key,
    occurred_at timestamptz not null default now(),
    event_type varchar(80) not null,
    actor_type varchar(20) not null check (actor_type in ('USER', 'TECHNICAL')),
    actor_id varchar(160) not null,
    correlation_id varchar(160) not null,
    entity_type varchar(40) not null,
    entity_id varchar(160) not null,
    outcome varchar(20) not null check (outcome in ('SUCCESS', 'FAILURE', 'DENIED', 'PENDING')),
    metadata jsonb not null default '{}'::jsonb,
    reason varchar(500),
    retention_until timestamptz not null,
    constraint chk_audit_metadata_object check (jsonb_typeof(metadata) = 'object')
);

create index idx_audit_events_occurred on audit_events(occurred_at desc, id);
create index idx_audit_events_type on audit_events(event_type, occurred_at desc);
create index idx_audit_events_actor on audit_events(actor_id, occurred_at desc);
create index idx_audit_events_entity on audit_events(entity_type, entity_id, occurred_at desc);
create index idx_audit_events_correlation on audit_events(correlation_id);
create index idx_audit_events_retention on audit_events(retention_until);

create or replace function guard_audit_event_mutation()
returns trigger language plpgsql as $$
begin
    if coalesce(current_setting('signflow.audit_retention_allowed', true), 'false') <> 'true' then
        raise exception 'Audit events are append-only' using errcode = '42501';
    end if;
    if tg_op = 'UPDATE' then
        raise exception 'Audit events cannot be updated' using errcode = '42501';
    end if;
    return old;
end;
$$;

create trigger trg_guard_audit_event_mutation
before update or delete on audit_events
for each row execute function guard_audit_event_mutation();

create or replace function append_audit_event(
    p_event_type varchar, p_actor_type varchar, p_actor_id varchar, p_correlation_id varchar,
    p_entity_type varchar, p_entity_id varchar, p_outcome varchar, p_metadata jsonb,
    p_reason varchar, p_occurred_at timestamptz default now()
) returns void language plpgsql as $$
declare retention integer;
begin
    select retention_days into retention from audit_retention_policy where id = 1;
    insert into audit_events (
        id, occurred_at, event_type, actor_type, actor_id, correlation_id,
        entity_type, entity_id, outcome, metadata, reason, retention_until
    ) values (
        gen_random_uuid(), p_occurred_at, p_event_type, p_actor_type, p_actor_id,
        p_correlation_id, p_entity_type, p_entity_id, p_outcome,
        coalesce(p_metadata, '{}'::jsonb), nullif(trim(p_reason), ''),
        p_occurred_at + make_interval(days => retention)
    );
end;
$$;

create or replace function audit_report_workflow_event()
returns trigger language plpgsql as $$
declare event_name varchar;
begin
    event_name := case
        when new.operation_type in ('ASSIGN_SIGNER', 'CLEAR_SIGNER') then 'SIGNER_ASSIGNMENT_CHANGED'
        when new.first_preview then 'DOCUMENT_FIRST_PREVIEWED'
        else 'REPORT_STATE_CHANGED'
    end;
    perform append_audit_event(event_name, 'USER', new.actor_username, new.operation_key,
        'REPORT', new.report_id::varchar, 'SUCCESS',
        jsonb_build_object('operation', new.operation_type, 'fromState', new.from_state,
            'toState', new.to_state, 'workflowVersion', new.resulting_version,
            'missingFieldCount', case when new.missing_fields = '' then 0 else array_length(string_to_array(new.missing_fields, ','), 1) end),
        new.reason, new.created_at);
    return new;
end;
$$;

create trigger trg_audit_report_workflow_event
after insert on report_workflow_events
for each row execute function audit_report_workflow_event();

create or replace function audit_report_review_decision()
returns trigger language plpgsql as $$
begin
    perform append_audit_event('REVIEW_' || new.decision_type, 'USER', new.actor_username,
        new.operation_key, 'REPORT', new.report_id::varchar, 'SUCCESS',
        jsonb_build_object('actorRole', new.actor_role, 'fromState', new.from_state,
            'toState', new.to_state, 'workflowVersion', new.resulting_version),
        new.reason, new.created_at);
    return new;
end;
$$;

create trigger trg_audit_report_review_decision
after insert on report_review_decisions
for each row execute function audit_report_review_decision();

create or replace function audit_clinical_document()
returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        perform append_audit_event('DOCUMENT_UPLOADED', 'USER', new.uploaded_by,
            'upload-' || new.id::varchar, 'DOCUMENT', new.id::varchar, 'SUCCESS',
            jsonb_build_object('reportId', new.report_id, 'version', new.version,
                'mimeType', new.mime_type, 'sizeBytes', new.size_bytes), null, new.uploaded_at);
    elsif new.status = 'DELETED' and old.status <> 'DELETED' then
        perform append_audit_event('DOCUMENT_REMOVED', 'USER', new.deleted_by,
            'delete-' || new.id::varchar, 'DOCUMENT', new.id::varchar, 'SUCCESS',
            jsonb_build_object('reportId', new.report_id, 'version', new.version), null, new.deleted_at);
    end if;
    return new;
end;
$$;

create trigger trg_audit_clinical_document
after insert or update on clinical_documents
for each row execute function audit_clinical_document();

create or replace function audit_signature_batch()
returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        perform append_audit_event('SIGNATURE_BATCH_CREATED', 'USER', new.signer_username,
            new.create_operation_key, 'SIGNATURE_BATCH', new.id::varchar, 'SUCCESS',
            jsonb_build_object('selectionMode', new.selection_mode, 'documentCount', new.total_count), null, new.created_at);
    elsif new.state is distinct from old.state then
        perform append_audit_event('SIGNATURE_BATCH_STATE_CHANGED', 'USER', new.signer_username,
            'batch-state-' || new.id::varchar || '-' || new.version::varchar,
            'SIGNATURE_BATCH', new.id::varchar,
            case when new.state = 'FAILED' then 'FAILURE' when new.state in ('DRAFT','CONFIRMED','RUNNING') then 'PENDING' else 'SUCCESS' end,
            jsonb_build_object('fromState', old.state, 'toState', new.state,
                'successCount', new.success_count, 'failureCount', new.failure_count), null, now());
    end if;
    return new;
end;
$$;

create trigger trg_audit_signature_batch
after insert or update on signature_batches
for each row execute function audit_signature_batch();

create or replace function audit_signature_attempt()
returns trigger language plpgsql as $$
declare actor varchar;
begin
    select signer_username into actor from signature_batches where id = new.batch_id;
    perform append_audit_event(
        case when tg_op = 'INSERT' then 'SIGNATURE_ATTEMPT_CREATED' else 'SIGNATURE_PROVIDER_OUTCOME' end,
        'USER', actor,
        'signature-attempt-' || new.id::varchar || '-' || new.retry_count::varchar || '-' || new.state,
        'SIGNATURE_ATTEMPT', new.id::varchar,
        case when new.state = 'FAILED' then 'FAILURE' when new.state in ('PENDING','SIGNING') then 'PENDING' else 'SUCCESS' end,
        jsonb_build_object('batchId', new.batch_id, 'reportId', new.report_id,
            'state', new.state, 'retryCount', new.retry_count, 'errorCode', new.error_code),
        null, coalesce(new.completed_at, new.started_at, now()));
    return new;
end;
$$;

create trigger trg_audit_signature_attempt
after insert or update of state, retry_count on signature_attempts
for each row execute function audit_signature_attempt();

insert into admin_ui_texts (text_key, text_value) values
('button.searchAudit', 'Cerca eventi'),
('button.exportAudit', 'Esporta CSV'),
('button.saveRetention', 'Salva retention'),
('button.applyRetention', 'Applica retention'),
('button.openTimeline', 'Apri timeline'),
('label.auditTitle', 'Audit e monitoraggio'),
('label.reportTimeline', 'Timeline della pratica');

select append_audit_event('FSE_OPERATION_RESERVED', 'TECHNICAL', 'demo.fse-adapter',
    'demo-fse-0001', 'REPORT', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1', 'PENDING',
    '{"operation":"FUTURE_TEST","environment":"FICTIONAL"}'::jsonb, 'Funzione futura non ancora attiva',
    '2026-08-05T10:00:00+02:00');
select append_audit_event('CONSERVATION_OPERATION_RESERVED', 'TECHNICAL', 'demo.preservation-adapter',
    'demo-preservation-0001', 'REPORT', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1', 'PENDING',
    '{"operation":"FUTURE_TEST","environment":"FICTIONAL"}'::jsonb, 'Funzione futura non ancora attiva',
    '2026-08-05T10:05:00+02:00');
