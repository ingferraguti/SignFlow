create table report_authorized_groups (
    report_id uuid not null references reports(id) on delete cascade,
    group_id uuid not null references user_groups(id) on delete cascade,
    primary key (report_id, group_id)
);

create table report_authorized_partitions (
    report_id uuid not null references reports(id) on delete cascade,
    partition_id uuid not null references partitions(id) on delete cascade,
    primary key (report_id, partition_id)
);

create index idx_report_authorized_groups_group on report_authorized_groups(group_id);
create index idx_report_authorized_partitions_partition on report_authorized_partitions(partition_id);

insert into partitions (id, code, name, active) values
('11111111-1111-1111-1111-111111111112', 'OTHER-DEMO', 'Partizione fittizia separata', true);

insert into companies (id, code, name, partition_id, active) values
('22222222-2222-2222-2222-222222222223', 'OTHER-CARE', 'Azienda sanitaria fittizia separata',
 '11111111-1111-1111-1111-111111111112', true);

insert into user_groups (id, code, name, partition_id, active) values
('44444444-4444-4444-4444-444444444443', 'OTHER-SIGNERS', 'Firmatari fittizi separati',
 '11111111-1111-1111-1111-111111111112', true);

insert into application_users (
    id, username, oidc_subject, first_name, last_name, email, fiscal_code, signer_fiscal_code,
    counter_signer_fiscal_code, active, partition_id, company_id
) values (
    '55555555-5555-5555-5555-555555555553', 'other.signer', 'other.signer', 'Altro', 'Firmatario Fittizio',
    'other.signer@signflow.invalid', 'TSTALT70C03H501Z', 'TSTALT70C03H501Z', null, true,
    '11111111-1111-1111-1111-111111111112', '22222222-2222-2222-2222-222222222223'
);

insert into application_user_roles (user_id, role_id) values
('55555555-5555-5555-5555-555555555553', '33333333-3333-3333-3333-333333333332');

insert into application_user_groups (user_id, group_id) values
('55555555-5555-5555-5555-555555555553', '44444444-4444-4444-4444-444444444443');

insert into practices (id, practice_identifier, external_reference, description) values
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'PRACTICE-DEMO-003', 'CASE-EXT-003', 'Contenitore fittizio di isolamento autorizzativo');

insert into patient_metadata (id, patient_identifier, first_name, last_name, fiscal_code, birth_date) values
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3', 'PAT-DEMO-003', 'Carla', 'Inventata', 'TSTCRL90C43H501W', '1990-03-03'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb4', 'PAT-DEMO-004', 'Dario', 'Simulato', 'TSTDRA85D04H501V', '1985-04-04');

insert into reports (
    id, internal_identifier, external_identifier, fse_identifier, practice_id, patient_metadata_id,
    assigned_signer_id, source_system_id, document_type, department, produced_at, modified_at,
    pdf_a3_conversion, visible_signature, multiple_signature, send_unsigned, create_cda, passthrough, state
) values
('cccccccc-cccc-cccc-cccc-ccccccccccc5', 'RPT-INT-005', 'RPT-EXT-005', null,
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3',
 '55555555-5555-5555-5555-555555555553', '66666666-6666-6666-6666-666666666661',
 'PDF-REF', 'Reparto separato', '2026-07-18T09:00:00+02:00', '2026-07-18T09:10:00+02:00',
 false, true, false, false, false, true, 'READY_TO_SIGN'),
('cccccccc-cccc-cccc-cccc-ccccccccccc6', 'RPT-INT-006', 'RPT-EXT-006', null,
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb4',
 '55555555-5555-5555-5555-555555555553', '66666666-6666-6666-6666-666666666661',
 'PDF-REF', 'Diagnostica fittizia', '2026-07-20T10:00:00+02:00', '2026-07-20T10:10:00+02:00',
 false, true, false, false, false, true, 'READY_TO_SIGN');

insert into report_authorized_groups (report_id, group_id) values
('cccccccc-cccc-cccc-cccc-ccccccccccc3', '44444444-4444-4444-4444-444444444442');

insert into report_authorized_partitions (report_id, partition_id) values
('cccccccc-cccc-cccc-cccc-ccccccccccc6', '11111111-1111-1111-1111-111111111111');
