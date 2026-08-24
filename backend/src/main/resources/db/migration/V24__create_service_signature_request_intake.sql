create table integration_document_subtypes (
    document_type varchar(20) not null,
    code varchar(80) not null,
    display_name varchar(160) not null,
    source_reference varchar(500) not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (document_type, code),
    constraint chk_integration_document_type check (document_type in ('HEALTHCARE', 'ADMINISTRATIVE')),
    constraint chk_integration_document_subtype_code check (code ~ '^[A-Z][A-Z0-9_]{1,79}$')
);

insert into integration_document_subtypes (document_type, code, display_name, source_reference) values
('HEALTHCARE', 'LABORATORY_REPORT', 'Referto di laboratorio', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'RADIOLOGY_REPORT', 'Referto di radiologia', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'SPECIALIST_OUTPATIENT_REPORT', 'Referto di specialistica ambulatoriale', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'PATHOLOGY_REPORT', 'Referto di anatomia patologica', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'EMERGENCY_DEPARTMENT_REPORT', 'Verbale di pronto soccorso', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'DISCHARGE_LETTER', 'Lettera di dimissione', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'PATIENT_SUMMARY', 'Profilo sanitario sintetico', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'PHARMACEUTICAL_PRESCRIPTION', 'Prescrizione farmaceutica', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'SPECIALIST_PRESCRIPTION', 'Prescrizione specialistica', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'CLINICAL_RECORD', 'Cartella clinica', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'DRUG_DISPENSATION', 'Erogazione farmaci', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'SINGLE_VACCINATION_RECORD', 'Scheda singola vaccinazione', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'VACCINATION_CERTIFICATE', 'Certificato vaccinale', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'SPECIALIST_CARE_DELIVERY', 'Erogazione di prestazioni di assistenza specialistica', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'IMPLANT_CARD', 'Tessera portatore di impianto', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'PREVENTION_INVITATION', 'Lettera di invito per screening, vaccinazione o prevenzione', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('HEALTHCARE', 'PERSONAL_HEALTH_NOTEBOOK', 'Taccuino personale dell’assistito', 'https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/'),
('ADMINISTRATIVE', 'CONTRACT', 'Contratto', 'https://www.agendadigitale.eu/documenti/intelligenza-artificiale-nella-pa-come-cambia-la-gestione-documentale/'),
('ADMINISTRATIVE', 'RESOLUTION', 'Delibera', 'https://www.agendadigitale.eu/documenti/intelligenza-artificiale-nella-pa-come-cambia-la-gestione-documentale/'),
('ADMINISTRATIVE', 'OPINION', 'Parere', 'https://www.agendadigitale.eu/documenti/intelligenza-artificiale-nella-pa-come-cambia-la-gestione-documentale/'),
('ADMINISTRATIVE', 'TECHNICAL_REPORT', 'Relazione tecnica', 'https://www.agendadigitale.eu/documenti/intelligenza-artificiale-nella-pa-come-cambia-la-gestione-documentale/'),
('ADMINISTRATIVE', 'EXPENSE_REPORT', 'Nota spese', 'https://www.agendadigitale.eu/documenti/dematerializzazione-delle-note-spese-i-requisiti-e-le-indicazioni-dellagenzia-delle-entrate/'),
('ADMINISTRATIVE', 'INVOICE', 'Fattura', 'https://www.agendadigitale.eu/documenti/conservazione-dei-documenti-digitali-i-metodi-e-le-differenze-col-backup/'),
('ADMINISTRATIVE', 'DELIVERY_NOTE', 'Bolla di accompagnamento', 'https://www.agendadigitale.eu/documenti/conservazione-dei-documenti-digitali-i-metodi-e-le-differenze-col-backup/'),
('ADMINISTRATIVE', 'PROJECT_DOCUMENT', 'Documento di progetto', 'https://www.agendadigitale.eu/documenti/conservazione-dei-documenti-digitali-i-metodi-e-le-differenze-col-backup/'),
('ADMINISTRATIVE', 'ADMINISTRATIVE_ACT', 'Atto amministrativo', 'https://www.agendadigitale.eu/documenti/conservazione-dei-documenti-digitali-i-metodi-e-le-differenze-col-backup/'),
('ADMINISTRATIVE', 'PROTOCOLLED_COMMUNICATION', 'Comunicazione protocollata', 'https://www.agendadigitale.eu/documenti/gestione-documentale-tutte-le-novita-delle-linee-guida-agid-per-imprese-e-professionisti/');

create table service_signature_requests (
    id uuid primary key,
    external_request_id varchar(120) not null,
    source_system_id uuid not null references source_systems(id),
    document_type varchar(20) not null,
    document_subtype varchar(80) not null,
    signer_natural_person_id uuid not null references natural_persons(id),
    status varchar(30) not null default 'PENDING_SIGNATURE',
    idempotency_key varchar(160) not null,
    request_fingerprint char(64) not null,
    correlation_id varchar(160) not null,
    technical_actor varchar(160) not null,
    submitted_at timestamptz not null default now(),
    signed_at timestamptz,
    signature_count integer,
    signature_validation varchar(40),
    conservation_status varchar(30) not null default 'NOT_REQUESTED',
    conservation_sent_at timestamptz,
    conservation_completed_at timestamptz,
    conservation_remote_reference varchar(240),
    conservation_error_code varchar(120),
    version bigint not null default 0,
    constraint fk_service_signature_request_subtype foreign key (document_type, document_subtype)
        references integration_document_subtypes(document_type, code),
    constraint chk_service_signature_request_type check (document_type in ('HEALTHCARE', 'ADMINISTRATIVE')),
    constraint chk_service_signature_request_status check (status in
        ('PENDING_SIGNATURE', 'SIGNING', 'SIGNED', 'REJECTED', 'FAILED')),
    constraint chk_service_signature_request_signature check (
        (status <> 'SIGNED' and signed_at is null and signature_count is null and signature_validation is null)
        or (status = 'SIGNED' and signed_at is not null and signature_count > 0
            and signature_validation in ('TRUSTED_VALID', 'TECHNICALLY_VALID'))),
    constraint chk_service_signature_request_conservation check (conservation_status in
        ('NOT_REQUESTED', 'PENDING', 'SENT', 'ACCEPTED', 'REJECTED', 'FAILED')),
    constraint chk_service_signature_request_fingerprint check (request_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint uq_service_signature_request_idempotency unique (source_system_id, idempotency_key),
    constraint uq_service_signature_request_external unique (source_system_id, external_request_id)
);

create table service_signature_request_documents (
    id uuid primary key,
    request_id uuid not null references service_signature_requests(id) on delete restrict,
    artifact_type varchar(30) not null,
    sha256 char(64) not null,
    mime_type varchar(120) not null,
    size_bytes bigint not null,
    original_filename varchar(255) not null,
    object_key varchar(500) not null unique,
    pdfa_part varchar(2),
    pdfa_conformance varchar(2),
    pdfa_validator varchar(120),
    page_count integer,
    created_at timestamptz not null default now(),
    constraint chk_service_signature_document_sha256 check (sha256 ~ '^[0-9a-f]{64}$'),
    constraint chk_service_signature_document_size check (size_bytes > 0),
    constraint uq_service_signature_document_artifact unique (request_id, artifact_type),
    constraint chk_service_signature_document_artifact check (artifact_type in
        ('ORIGINAL', 'NORMALIZED_PDFA3', 'SIGNED')),
    constraint chk_service_signature_document_pdf check (
        artifact_type = 'ORIGINAL' or mime_type = 'application/pdf'),
    constraint chk_service_signature_document_pdfa check (
        (artifact_type = 'NORMALIZED_PDFA3' and pdfa_part = '3' and pdfa_conformance = 'B'
            and pdfa_validator is not null and page_count > 0)
        or (artifact_type <> 'NORMALIZED_PDFA3' and pdfa_part is null
            and pdfa_conformance is null and pdfa_validator is null and page_count is null))
);

create table service_signature_request_events (
    id uuid primary key,
    request_id uuid not null references service_signature_requests(id),
    event_type varchar(60) not null,
    status varchar(30) not null,
    actor varchar(160) not null,
    correlation_id varchar(160) not null,
    created_at timestamptz not null default now()
);

create table service_signature_request_commands (
    request_id uuid not null references service_signature_requests(id),
    idempotency_key varchar(160) not null,
    command_type varchar(40) not null,
    request_fingerprint char(64) not null,
    created_at timestamptz not null default now(),
    primary key (request_id, idempotency_key),
    constraint chk_service_signature_command_type check (command_type in
        ('REGISTER_SIGNED_DOCUMENT', 'REGISTER_CONSERVATION_STATUS')),
    constraint chk_service_signature_command_fingerprint check (request_fingerprint ~ '^[0-9a-f]{64}$')
);

create index idx_service_signature_requests_submitted on service_signature_requests(submitted_at desc, id);
create index idx_service_signature_requests_signer on service_signature_requests(signer_natural_person_id, status);
create index idx_service_signature_requests_correlation on service_signature_requests(correlation_id);
create index idx_service_signature_request_events_request on service_signature_request_events(request_id, created_at);
create index idx_service_signature_request_documents_request on service_signature_request_documents(request_id, artifact_type);

create or replace function guard_service_signature_request_event_mutation()
returns trigger language plpgsql as $$
begin
    raise exception 'Service signature request events are append-only' using errcode = '42501';
end;
$$;

create trigger trg_guard_service_signature_request_event_mutation
before update or delete on service_signature_request_events
for each row execute function guard_service_signature_request_event_mutation();

create or replace function record_service_signature_request_received()
returns trigger language plpgsql as $$
begin
    insert into service_signature_request_events
        (id, request_id, event_type, status, actor, correlation_id, created_at)
    values (gen_random_uuid(), new.id, 'SIGNATURE_REQUEST_RECEIVED', new.status,
        new.technical_actor, new.correlation_id, new.submitted_at);
    perform append_audit_event('SERVICE_SIGNATURE_REQUEST_RECEIVED', 'TECHNICAL', new.technical_actor,
        new.correlation_id, 'SIGNATURE_REQUEST', new.id::varchar, 'PENDING',
        jsonb_build_object('sourceSystemId', new.source_system_id, 'status', new.status), null, new.submitted_at);
    return new;
end;
$$;

create trigger trg_record_service_signature_request_received
after insert on service_signature_requests
for each row execute function record_service_signature_request_received();
