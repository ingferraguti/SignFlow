alter table fse_document_types
    add column approval_required boolean not null default true,
    add column preview_required boolean not null default true;
