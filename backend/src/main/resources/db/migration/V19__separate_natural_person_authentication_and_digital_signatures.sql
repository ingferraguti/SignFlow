create table natural_persons (
    id uuid primary key,
    first_name varchar(120) not null,
    last_name varchar(120) not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table natural_person_identifiers (
    id uuid primary key,
    natural_person_id uuid not null references natural_persons(id) on delete cascade,
    scheme varchar(40) not null,
    issuing_country char(2) not null,
    issuer varchar(200) not null,
    normalized_value varchar(200) not null,
    verified boolean not null default false,
    verified_at timestamptz,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    constraint chk_person_identifier_scheme check (scheme in
        ('IT_TAX_CODE','EIDAS_PERSON_IDENTIFIER','NATIONAL_ID')),
    constraint chk_person_identifier_country check (issuing_country ~ '^[A-Z]{2}$'),
    constraint uq_person_identifier unique (scheme, issuing_country, issuer, normalized_value)
);

insert into natural_persons (id, first_name, last_name)
select (md5('signflow-person:' || identity_key))::uuid,
       min(first_name), min(last_name)
from (
    select coalesce(nullif(upper(trim(coalesce(signer_fiscal_code, fiscal_code))), ''), id::text) identity_key,
           first_name, last_name
    from application_users
) source
group by identity_key;

insert into natural_person_identifiers
    (id, natural_person_id, scheme, issuing_country, issuer, normalized_value, verified, verified_at)
select (md5('signflow-identifier:' || normalized_code))::uuid,
       (md5('signflow-person:' || normalized_code))::uuid,
       'IT_TAX_CODE', 'IT', 'AGENZIA_ENTRATE', normalized_code, true, now()
from (
    select distinct upper(trim(coalesce(signer_fiscal_code, fiscal_code))) normalized_code
    from application_users
    where nullif(trim(coalesce(signer_fiscal_code, fiscal_code)), '') is not null
) identifiers;

alter table application_users add column natural_person_id uuid;

update application_users
set natural_person_id = (md5('signflow-person:' ||
    coalesce(nullif(upper(trim(coalesce(signer_fiscal_code, fiscal_code))), ''), id::text)))::uuid;

alter table application_users
    alter column natural_person_id set not null,
    add constraint fk_application_user_natural_person
        foreign key (natural_person_id) references natural_persons(id);

create table authentication_identities (
    id uuid primary key,
    application_user_id uuid not null references application_users(id) on delete cascade,
    natural_person_id uuid not null references natural_persons(id),
    issuer varchar(300) not null,
    subject varchar(200) not null,
    authentication_method varchar(40) not null default 'OIDC',
    assurance_level varchar(40),
    active boolean not null default true,
    last_authenticated_at timestamptz,
    created_at timestamptz not null default now(),
    constraint uq_authentication_identity unique (issuer, subject)
);

create table natural_person_identity_events (
    id uuid primary key,
    application_user_id uuid not null references application_users(id),
    previous_natural_person_id uuid references natural_persons(id),
    resulting_natural_person_id uuid not null references natural_persons(id),
    event_type varchar(40) not null,
    reason varchar(500),
    actor varchar(160) not null,
    created_at timestamptz not null default now(),
    constraint chk_person_identity_event_type check (event_type in ('PROFILE_LINKED','IDENTITY_CORRECTED')),
    constraint chk_person_identity_correction_reason check
        (event_type <> 'IDENTITY_CORRECTED' or length(trim(reason)) >= 10)
);

insert into authentication_identities
    (id, application_user_id, natural_person_id, issuer, subject, authentication_method)
select (md5('signflow-auth:' || id::text))::uuid, id, natural_person_id,
       'legacy://signflow', oidc_subject, 'OIDC'
from application_users;

alter table signature_accounts
    add column natural_person_id uuid,
    add column display_name varchar(160),
    add column signature_type varchar(40) not null default 'REMOTE',
    add column qualified boolean not null default false;

update signature_accounts sa
set natural_person_id=u.natural_person_id,
    display_name=coalesce(nullif(sa.certificate_alias, ''), sa.account_alias)
from application_users u where u.id=sa.application_user_id;

alter table signature_accounts
    alter column natural_person_id set not null,
    alter column display_name set not null,
    alter column application_user_id drop not null,
    add constraint fk_signature_account_natural_person
        foreign key (natural_person_id) references natural_persons(id),
    add constraint chk_signature_type check (signature_type in ('REMOTE','LOCAL_TEST','MOCK'));

alter table application_users add column preferred_signature_account_id uuid;
alter table application_users add constraint fk_user_preferred_signature
    foreign key (preferred_signature_account_id) references signature_accounts(id);

update application_users u
set preferred_signature_account_id=(
    select sa.id from signature_accounts sa
    where sa.natural_person_id=u.natural_person_id and sa.active=true
    order by sa.created_at, sa.id limit 1
);

alter table provider_sessions add column natural_person_id uuid;
update provider_sessions ps set natural_person_id=sa.natural_person_id
from signature_accounts sa where sa.id=ps.signature_account_id;
alter table provider_sessions alter column natural_person_id set not null;
alter table provider_sessions add constraint fk_provider_session_natural_person
    foreign key (natural_person_id) references natural_persons(id);

alter table signature_batches add column signer_natural_person_id uuid;
update signature_batches b set signer_natural_person_id=u.natural_person_id
from application_users u where u.username=b.signer_username;
alter table signature_batches alter column signer_natural_person_id set not null;
alter table signature_batches add constraint fk_signature_batch_natural_person
    foreign key (signer_natural_person_id) references natural_persons(id);
alter table signature_batches drop constraint uq_signature_batch_create;
alter table signature_batches add constraint uq_signature_batch_create_person
    unique (signer_natural_person_id, create_operation_key);

create index idx_application_users_person on application_users(natural_person_id);
create index idx_authentication_identities_person on authentication_identities(natural_person_id);
create index idx_signature_accounts_person on signature_accounts(natural_person_id, active);
create index idx_provider_sessions_person on provider_sessions(natural_person_id, expires_at desc);
create index idx_signature_batches_person on signature_batches(signer_natural_person_id, created_at desc);

-- Second fictional login profile for the same natural person. It proves that identity and profile are separate.
insert into application_users (
    id, username, oidc_subject, first_name, last_name, email, fiscal_code, signer_fiscal_code,
    counter_signer_fiscal_code, active, partition_id, company_id, natural_person_id,
    preferred_signature_account_id
) select
    '55555555-5555-5555-5555-555555555559', 'demo.signer.alt', 'demo.signer.alt',
    'Local Demo', 'Signer', 'demo.signer.alt@signflow.invalid', fiscal_code, signer_fiscal_code,
    null, true, partition_id, company_id, natural_person_id, preferred_signature_account_id
from application_users where id='55555555-5555-5555-5555-555555555552';

insert into application_user_roles (user_id, role_id)
values ('55555555-5555-5555-5555-555555555559', '33333333-3333-3333-3333-333333333332');
insert into application_user_groups (user_id, group_id)
values ('55555555-5555-5555-5555-555555555559', '44444444-4444-4444-4444-444444444442');
insert into authentication_identities
    (id, application_user_id, natural_person_id, issuer, subject, authentication_method, assurance_level)
select 'abababab-abab-abab-abab-ababababab19',
       '55555555-5555-5555-5555-555555555559', natural_person_id,
       'legacy://signflow-alt', 'demo.signer.alt', 'LDAP', 'DEMO'
from application_users where id='55555555-5555-5555-5555-555555555552';

insert into signature_accounts (
    id, application_user_id, natural_person_id, signature_provider_id, account_alias,
    provider_username, certificate_alias, display_name, signature_type, qualified, active
) select '88888888-8888-8888-8888-888888888889', null, natural_person_id,
         signature_provider_id, 'demo-signer-secondary', 'demo.signer.secondary',
         'CERT-DEMO-SECONDARY', 'Firma remota demo secondaria', 'MOCK', false, true
from signature_accounts where id='88888888-8888-8888-8888-888888888881';

update signature_accounts set display_name='Firma remota demo principale', signature_type='MOCK'
where id='88888888-8888-8888-8888-888888888881';
update application_users set preferred_signature_account_id='88888888-8888-8888-8888-888888888889'
where id='55555555-5555-5555-5555-555555555559';

insert into admin_ui_texts (text_key, text_value) values
('profile.naturalPerson', 'Persona naturale'),
('profile.personalIdentifier', 'Identificativo personale'),
('profile.authenticationAccounts', 'Account di accesso collegati'),
('profile.digitalSignatures', 'Firme digitali disponibili'),
('profile.preferredDigitalSignature', 'Firma digitale preferita'),
('button.changePreferredDigitalSignature', 'Imposta firma preferita')
on conflict (text_key) do nothing;
