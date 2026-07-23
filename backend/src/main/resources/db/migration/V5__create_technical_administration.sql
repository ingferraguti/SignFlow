create table source_systems (
    id uuid primary key,
    code varchar(60) not null unique,
    company_id uuid not null references companies(id),
    description varchar(300) not null,
    active boolean not null default true,
    cda_type varchar(60) not null,
    pdf_a3_conversion boolean not null default false,
    visible_signature boolean not null default false,
    multiple_signature boolean not null default false,
    send_unsigned boolean not null default false,
    create_cda boolean not null default false,
    passthrough boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_source_system_pipeline_mode check (not (create_cda and passthrough))
);

create table signature_providers (
    id uuid primary key,
    code varchar(60) not null unique,
    name varchar(160) not null,
    adapter_type varchar(80) not null,
    base_url varchar(500),
    authentication_mode varchar(40) not null,
    credential_reference varchar(200),
    supports_visible_signature boolean not null default false,
    supports_multiple_signature boolean not null default false,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_signature_provider_auth_mode check (authentication_mode in
        ('NONE', 'OTP', 'USERNAME_OTP', 'OAUTH2', 'CERTIFICATE', 'API_KEY_REFERENCE'))
);

create table signature_accounts (
    id uuid primary key,
    application_user_id uuid not null references application_users(id),
    signature_provider_id uuid not null references signature_providers(id),
    account_alias varchar(120) not null,
    provider_username varchar(160),
    certificate_alias varchar(200),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_signature_account unique (signature_provider_id, account_alias)
);

create table fse_facility_mappings (
    id uuid primary key,
    facility_code varchar(80) not null,
    facility_name varchar(200) not null,
    company_id uuid not null references companies(id),
    operating_unit varchar(160) not null,
    department varchar(160) not null,
    source_system_id uuid not null references source_systems(id),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_fse_facility_mapping unique (facility_code, company_id, operating_unit, department, source_system_id)
);

create index idx_source_systems_company on source_systems(company_id);
create index idx_source_systems_active on source_systems(active);
create index idx_signature_accounts_user on signature_accounts(application_user_id);
create index idx_signature_accounts_provider on signature_accounts(signature_provider_id);
create index idx_fse_facility_company on fse_facility_mappings(company_id);
create index idx_fse_facility_source_system on fse_facility_mappings(source_system_id);

insert into source_systems (
    id, code, company_id, description, active, cda_type, pdf_a3_conversion,
    visible_signature, multiple_signature, send_unsigned, create_cda, passthrough
) values (
    '66666666-6666-6666-6666-666666666661', 'LIS-DEMO',
    '22222222-2222-2222-2222-222222222221', 'Sistema erogante LIS dimostrativo', true,
    'CDA2-REF', true, true, false, false, true, false
);

insert into signature_providers (
    id, code, name, adapter_type, base_url, authentication_mode, credential_reference,
    supports_visible_signature, supports_multiple_signature, active
) values (
    '77777777-7777-7777-7777-777777777771', 'MOCK-REMOTE', 'Provider firma demo',
    'MOCK', 'http://mock-signature-provider:8080', 'USERNAME_OTP', 'secret://signflow/providers/mock-remote',
    true, true, true
);

insert into signature_accounts (
    id, application_user_id, signature_provider_id, account_alias, provider_username, certificate_alias, active
) values (
    '88888888-8888-8888-8888-888888888881', '55555555-5555-5555-5555-555555555552',
    '77777777-7777-7777-7777-777777777771', 'demo-signer', 'demo.signer', 'CERT-DEMO-SIGNER', true
);

insert into fse_facility_mappings (
    id, facility_code, facility_name, company_id, operating_unit, department, source_system_id, active
) values (
    '99999999-9999-9999-9999-999999999991', 'PRESIDIO-DEMO', 'Presidio dimostrativo',
    '22222222-2222-2222-2222-222222222221', 'UO Medicina', 'Medicina generale',
    '66666666-6666-6666-6666-666666666661', true
);
