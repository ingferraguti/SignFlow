alter table reports
    add column workflow_version bigint not null default 0,
    add column first_previewed_at timestamptz;

create table report_workflow_events (
    id uuid primary key,
    report_id uuid not null references reports(id),
    operation_key varchar(120) not null,
    operation_type varchar(40) not null,
    request_fingerprint char(64) not null,
    from_state varchar(50) not null,
    to_state varchar(50) not null,
    previous_signer_id uuid references application_users(id),
    new_signer_id uuid references application_users(id),
    actor_username varchar(160) not null,
    reason varchar(500),
    missing_fields varchar(1000) not null default '',
    previous_version bigint not null,
    resulting_version bigint not null,
    first_preview boolean not null default false,
    created_at timestamptz not null default now(),
    constraint uq_report_workflow_operation unique (report_id, operation_key),
    constraint chk_report_workflow_versions check (
        previous_version >= 0 and resulting_version >= previous_version
    ),
    constraint chk_report_workflow_admin_reason check (
        operation_type <> 'ADMIN_CORRECTION' or length(trim(reason)) > 0
    )
);

create index idx_report_workflow_events_report_created
    on report_workflow_events(report_id, created_at desc);
create index idx_reports_workflow_version on reports(id, workflow_version);

create or replace function guard_report_workflow_changes()
returns trigger
language plpgsql
as $$
begin
    if (new.state is distinct from old.state
        or new.assigned_signer_id is distinct from old.assigned_signer_id)
       and coalesce(current_setting('signflow.workflow_transition_allowed', true), 'false') <> 'true' then
        raise exception 'Report state and signer can only be changed by the workflow service'
            using errcode = '42501';
    end if;
    return new;
end;
$$;

create trigger trg_guard_report_workflow_changes
before update on reports
for each row execute function guard_report_workflow_changes();

insert into admin_ui_texts (text_key, text_value) values
('button.assignSigner', 'Assegna firmatario'),
('button.clearSigner', 'Rimuovi assegnazione'),
('button.evaluateReadiness', 'Verifica completezza'),
('button.adminCorrection', 'Applica correzione'),
('button.refreshWorkflow', 'Aggiorna workflow');
