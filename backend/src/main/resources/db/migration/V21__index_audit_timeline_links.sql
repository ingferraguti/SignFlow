create index idx_audit_document_report_link
    on audit_events (entity_id, (metadata ->> 'reportId'))
    where entity_type = 'DOCUMENT';

create index idx_audit_attempt_batch_report_link
    on audit_events ((metadata ->> 'batchId'), (metadata ->> 'reportId'))
    where entity_type = 'SIGNATURE_ATTEMPT';
