create table clinical_documents (
    id uuid primary key,
    report_id uuid not null references reports(id),
    sha256 char(64) not null,
    mime_type varchar(120) not null,
    size_bytes bigint not null,
    version integer not null,
    original_filename varchar(255) not null,
    object_key varchar(500) not null unique,
    uploaded_by varchar(160) not null,
    uploaded_at timestamptz not null default now(),
    status varchar(30) not null default 'ACTIVE',
    deleted_at timestamptz,
    deleted_by varchar(160),
    constraint uq_clinical_document_report_version unique (report_id, version),
    constraint chk_clinical_document_sha256 check (sha256 ~ '^[0-9a-f]{64}$'),
    constraint chk_clinical_document_size check (size_bytes > 0),
    constraint chk_clinical_document_version check (version > 0),
    constraint chk_clinical_document_status check (status in ('ACTIVE', 'DELETED')),
    constraint chk_clinical_document_deletion check (
        (status = 'ACTIVE' and deleted_at is null and deleted_by is null)
        or (status = 'DELETED' and deleted_at is not null and deleted_by is not null)
    )
);

create index idx_clinical_documents_report on clinical_documents(report_id, version desc);
create index idx_clinical_documents_status on clinical_documents(status);
create index idx_clinical_documents_sha256 on clinical_documents(sha256);
