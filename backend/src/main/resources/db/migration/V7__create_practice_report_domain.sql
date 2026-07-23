create table practices (
    id uuid primary key,
    practice_identifier varchar(80) not null unique,
    external_reference varchar(120),
    description varchar(300) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table patient_metadata (
    id uuid primary key,
    patient_identifier varchar(80) not null unique,
    first_name varchar(120) not null,
    last_name varchar(120) not null,
    fiscal_code varchar(32),
    birth_date date
);

create table reports (
    id uuid primary key,
    internal_identifier varchar(80) not null unique,
    external_identifier varchar(120) unique,
    fse_identifier varchar(120) unique,
    practice_id uuid not null references practices(id),
    patient_metadata_id uuid not null references patient_metadata(id),
    assigned_signer_id uuid references application_users(id),
    source_system_id uuid not null references source_systems(id),
    document_type varchar(80) not null,
    department varchar(160) not null,
    produced_at timestamptz not null,
    modified_at timestamptz not null,
    signed_at timestamptz,
    pdf_a3_conversion boolean not null default false,
    visible_signature boolean not null default false,
    multiple_signature boolean not null default false,
    send_unsigned boolean not null default false,
    create_cda boolean not null default false,
    passthrough boolean not null default false,
    state varchar(50) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_report_state check (state in (
        'RECEIVED', 'PARSED', 'INCOMPLETE', 'MISSING_SIGNER', 'READY_TO_SIGN', 'PREVIEWED',
        'REVIEW_PENDING', 'APPROVED', 'SIGN_BATCH_CREATED', 'SIGNING', 'SIGNED', 'SIGN_ERROR',
        'FSE_VALIDATION_ERROR', 'FSE_SENT', 'FSE_ACCEPTED', 'FSE_REJECTED',
        'CONSERVATION_SENT', 'CONSERVATION_ACCEPTED'
    )),
    constraint chk_report_pipeline_mode check (not (create_cda and passthrough)),
    constraint chk_report_signed_date check (signed_at is null or signed_at >= produced_at)
);

create index idx_reports_practice on reports(practice_id);
create index idx_reports_patient on reports(patient_metadata_id);
create index idx_reports_signer on reports(assigned_signer_id);
create index idx_reports_source_system on reports(source_system_id);
create index idx_reports_state on reports(state);
create index idx_reports_department on reports(department);
create index idx_reports_produced_at on reports(produced_at);
create index idx_reports_modified_at on reports(modified_at);
create index idx_reports_signed_at on reports(signed_at);
create index idx_patient_metadata_name on patient_metadata(last_name, first_name);
create index idx_patient_metadata_fiscal_code on patient_metadata(fiscal_code);

insert into practices (id, practice_identifier, external_reference, description) values
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'PRACTICE-DEMO-001', 'CASE-EXT-001', 'Contenitore dimostrativo cardiologia'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'PRACTICE-DEMO-002', 'CASE-EXT-002', 'Contenitore dimostrativo medicina');

insert into patient_metadata (id, patient_identifier, first_name, last_name, fiscal_code, birth_date) values
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', 'PAT-DEMO-001', 'Ada', 'Esempio', 'TSTDAA80A01H501X', '1980-01-01'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', 'PAT-DEMO-002', 'Bruno', 'Fittizio', 'TSTBRN75B02H501Y', '1975-02-02');

insert into reports (
    id, internal_identifier, external_identifier, fse_identifier, practice_id, patient_metadata_id,
    assigned_signer_id, source_system_id, document_type, department, produced_at, modified_at, signed_at,
    pdf_a3_conversion, visible_signature, multiple_signature, send_unsigned, create_cda, passthrough, state
) values
('cccccccc-cccc-cccc-cccc-ccccccccccc1', 'RPT-INT-001', 'RPT-EXT-001', 'FSE-DEMO-001',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
 '55555555-5555-5555-5555-555555555552', '66666666-6666-6666-6666-666666666661',
 'CDA2-REF', 'Cardiologia', '2026-07-01T08:30:00+02:00', '2026-07-01T10:00:00+02:00', '2026-07-01T09:45:00+02:00',
 true, true, false, false, true, false, 'SIGNED'),
('cccccccc-cccc-cccc-cccc-ccccccccccc2', 'RPT-INT-002', 'RPT-EXT-002', null,
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
 '55555555-5555-5555-5555-555555555552', '66666666-6666-6666-6666-666666666661',
 'PDF-REF', 'Cardiologia', '2026-07-05T11:00:00+02:00', '2026-07-05T11:20:00+02:00', null,
 true, true, true, false, false, true, 'READY_TO_SIGN'),
('cccccccc-cccc-cccc-cccc-ccccccccccc3', 'RPT-INT-003', 'RPT-EXT-003', 'FSE-DEMO-003',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2',
 null, '66666666-6666-6666-6666-666666666661',
 'CDA2-LDO', 'Medicina generale', '2026-07-10T14:15:00+02:00', '2026-07-10T14:30:00+02:00', null,
 true, false, false, false, true, false, 'MISSING_SIGNER'),
('cccccccc-cccc-cccc-cccc-ccccccccccc4', 'RPT-INT-004', null, null,
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2',
 '55555555-5555-5555-5555-555555555552', '66666666-6666-6666-6666-666666666661',
 'PDF-REF', 'Medicina generale', '2026-07-15T16:00:00+02:00', '2026-07-15T16:10:00+02:00', null,
 false, false, false, true, false, true, 'INCOMPLETE');
