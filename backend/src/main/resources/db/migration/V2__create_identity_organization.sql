create table partitions (
    id uuid primary key,
    code varchar(40) not null unique,
    name varchar(160) not null,
    active boolean not null default true
);

create table companies (
    id uuid primary key,
    code varchar(40) not null unique,
    name varchar(160) not null,
    partition_id uuid not null references partitions(id),
    active boolean not null default true
);

create table roles (
    id uuid primary key,
    code varchar(40) not null unique,
    description varchar(200) not null
);

create table user_groups (
    id uuid primary key,
    code varchar(60) not null unique,
    name varchar(160) not null,
    partition_id uuid not null references partitions(id),
    active boolean not null default true
);

create table application_users (
    id uuid primary key,
    username varchar(120) not null unique,
    oidc_subject varchar(160) not null unique,
    first_name varchar(120) not null,
    last_name varchar(120) not null,
    email varchar(200),
    fiscal_code varchar(32),
    signer_fiscal_code varchar(32),
    counter_signer_fiscal_code varchar(32),
    active boolean not null default true,
    partition_id uuid not null references partitions(id),
    company_id uuid not null references companies(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table application_user_roles (
    user_id uuid not null references application_users(id) on delete cascade,
    role_id uuid not null references roles(id),
    primary key (user_id, role_id)
);

create table application_user_groups (
    user_id uuid not null references application_users(id) on delete cascade,
    group_id uuid not null references user_groups(id),
    primary key (user_id, group_id)
);

create index idx_application_users_partition on application_users(partition_id);
create index idx_application_users_company on application_users(company_id);
create index idx_application_users_active on application_users(active);
create index idx_application_users_fiscal_code on application_users(fiscal_code);
create index idx_application_users_signer_fiscal_code on application_users(signer_fiscal_code);
create index idx_application_users_last_name on application_users(last_name);

insert into partitions (id, code, name, active) values
('11111111-1111-1111-1111-111111111111', 'LOCAL', 'Local demo partition', true);

insert into companies (id, code, name, partition_id, active) values
('22222222-2222-2222-2222-222222222221', 'SIGNFLOW', 'SignFlow Demo Healthcare Company', '11111111-1111-1111-1111-111111111111', true),
('22222222-2222-2222-2222-222222222222', 'AUSL-DEMO', 'AUSL Demo', '11111111-1111-1111-1111-111111111111', true);

insert into roles (id, code, description) values
('33333333-3333-3333-3333-333333333331', 'ADMINISTRATOR', 'Application administrator'),
('33333333-3333-3333-3333-333333333332', 'SIGNER', 'Clinical signer');

insert into user_groups (id, code, name, partition_id, active) values
('44444444-4444-4444-4444-444444444441', 'LOCAL-ADMINS', 'Local demo administrators', '11111111-1111-1111-1111-111111111111', true),
('44444444-4444-4444-4444-444444444442', 'LOCAL-SIGNERS', 'Local demo signers', '11111111-1111-1111-1111-111111111111', true);

insert into application_users (
    id, username, oidc_subject, first_name, last_name, email, fiscal_code, signer_fiscal_code,
    counter_signer_fiscal_code, active, partition_id, company_id
) values
('55555555-5555-5555-5555-555555555551', 'demo.admin', 'demo.admin', 'Local Demo', 'Administrator', 'demo.admin@signflow.local', null, null, null, true, '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222221'),
('55555555-5555-5555-5555-555555555552', 'demo.signer', 'demo.signer', 'Local Demo', 'Signer', 'demo.signer@signflow.local', 'DMSLGN80A01H501U', 'DMSLGN80A01H501U', null, true, '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222221');

insert into application_user_roles (user_id, role_id) values
('55555555-5555-5555-5555-555555555551', '33333333-3333-3333-3333-333333333331'),
('55555555-5555-5555-5555-555555555552', '33333333-3333-3333-3333-333333333332');

insert into application_user_groups (user_id, group_id) values
('55555555-5555-5555-5555-555555555551', '44444444-4444-4444-4444-444444444441'),
('55555555-5555-5555-5555-555555555552', '44444444-4444-4444-4444-444444444442');
