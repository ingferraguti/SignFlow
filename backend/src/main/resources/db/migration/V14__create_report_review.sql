insert into roles (id, code, description) values
('33333333-3333-3333-3333-333333333333', 'APPROVER', 'Clinical report approver');

insert into user_groups (id, code, name, partition_id, active) values
('44444444-4444-4444-4444-444444444444', 'LOCAL-APPROVERS', 'Local fictional approvers',
 '11111111-1111-1111-1111-111111111111', true);

insert into application_users (
    id, username, oidc_subject, first_name, last_name, email, fiscal_code, signer_fiscal_code,
    counter_signer_fiscal_code, active, partition_id, company_id
) values (
    '55555555-5555-5555-5555-555555555554', 'demo.approver', 'demo.approver',
    'Demo', 'Approvatore Fittizio', 'demo.approver@signflow.invalid',
    'TSTAPR81E05H501Q', null, 'TSTAPR81E05H501Q', true,
    '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222221'
);

insert into application_user_roles (user_id, role_id) values
('55555555-5555-5555-5555-555555555554', '33333333-3333-3333-3333-333333333333');

insert into application_user_groups (user_id, group_id) values
('55555555-5555-5555-5555-555555555554', '44444444-4444-4444-4444-444444444444');

alter table reports
    add column assigned_approver_id uuid references application_users(id),
    add column produced_by varchar(160),
    add column review_separation_required boolean not null default false,
    add column counter_signature_required boolean not null default false,
    add column counter_signer_id uuid references application_users(id),
    add column counter_signature_prepared_at timestamptz;

update reports
set produced_by='demo.producer',
    assigned_approver_id='55555555-5555-5555-5555-555555555554',
    review_separation_required=true,
    counter_signature_required=true
where id='cccccccc-cccc-cccc-cccc-ccccccccccc2';

create index idx_reports_approver on reports(assigned_approver_id);
create index idx_reports_counter_signer on reports(counter_signer_id);

create table report_review_decisions (
    id uuid primary key,
    report_id uuid not null references reports(id),
    operation_key varchar(120) not null,
    decision_type varchar(40) not null,
    request_fingerprint char(64) not null,
    actor_username varchar(160) not null,
    actor_role varchar(40) not null,
    reason varchar(500),
    from_state varchar(50) not null,
    to_state varchar(50) not null,
    previous_version bigint not null,
    resulting_version bigint not null,
    created_at timestamptz not null default now(),
    constraint uq_report_review_operation unique (report_id, operation_key),
    constraint chk_review_rejection_reason check (
        decision_type <> 'REJECTED' or length(trim(reason)) > 0
    ),
    constraint chk_review_return_reason check (
        decision_type <> 'RETURNED' or length(trim(reason)) > 0
    )
);

create index idx_report_review_decisions_timeline
    on report_review_decisions(report_id, created_at desc);

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
        or new.counter_signature_prepared_at is distinct from old.counter_signature_prepared_at)
       and coalesce(current_setting('signflow.workflow_transition_allowed', true), 'false') <> 'true' then
        raise exception 'Report workflow fields can only be changed by application workflow services'
            using errcode = '42501';
    end if;
    return new;
end;
$$;

insert into admin_ui_texts (text_key, text_value) values
('menu.approvals', 'Approvazioni'),
('button.requestApproval', 'Richiedi approvazione'),
('button.approveReport', 'Approva referto'),
('button.rejectReport', 'Rifiuta referto'),
('button.returnReview', 'Ritorna al passaggio precedente'),
('button.configureReview', 'Salva regole di revisione'),
('button.prepareCounterSignature', 'Predisponi controfirma'),
('button.openReviewDocument', 'Apri documento da revisionare');
