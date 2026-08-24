package it.signflow.ingestion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ServiceSignatureRequestRepository {
    private static final String REQUEST_SELECT = """
            select r.id,r.external_request_id,r.document_type,r.document_subtype,
                   st.display_name document_subtype_display_name,r.status,r.correlation_id,r.submitted_at,
                   r.request_fingerprint,r.idempotency_key,ss.code source_system_code,
                   np.id signer_natural_person_id,np.first_name signer_first_name,np.last_name signer_last_name,
                   o.id original_id,o.original_filename,o.mime_type original_mime_type,
                   o.size_bytes original_size_bytes,o.sha256 original_sha256,o.object_key original_object_key,
                   n.id normalized_id,n.size_bytes normalized_size_bytes,n.sha256 normalized_sha256,
                   n.object_key normalized_object_key,n.pdfa_part,n.pdfa_conformance,n.pdfa_validator,n.page_count,
                   s.id signed_id,s.size_bytes signed_size_bytes,s.sha256 signed_sha256,s.object_key signed_object_key,
                   r.signed_at,r.signature_count,r.signature_validation,r.conservation_status,
                   r.conservation_sent_at,r.conservation_completed_at,r.conservation_remote_reference,
                   r.conservation_error_code,r.version
            from service_signature_requests r
            join source_systems ss on ss.id=r.source_system_id
            join integration_document_subtypes st
              on st.document_type=r.document_type and st.code=r.document_subtype
            join natural_persons np on np.id=r.signer_natural_person_id
            join service_signature_request_documents o on o.request_id=r.id and o.artifact_type='ORIGINAL'
            join service_signature_request_documents n on n.request_id=r.id and n.artifact_type='NORMALIZED_PDFA3'
            left join service_signature_request_documents s on s.request_id=r.id and s.artifact_type='SIGNED'
            """;

    private final JdbcClient jdbc;
    ServiceSignatureRequestRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    List<CatalogSubtype> catalog() {
        return jdbc.sql("""
                select document_type,code,display_name,source_reference
                from integration_document_subtypes where active=true
                order by document_type desc,display_name,code
                """).query((rs, row) -> new CatalogSubtype(ServiceDocumentType.valueOf(rs.getString("document_type")),
                rs.getString("code"), rs.getString("display_name"), rs.getString("source_reference"))).list();
    }

    Optional<SourceSystem> activeSourceSystem(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return jdbc.sql("select id,code from source_systems where active=true and upper(code)=upper(:code)")
                .param("code", code.trim()).query((rs, row) -> new SourceSystem(
                        rs.getObject("id", UUID.class), rs.getString("code"))).optional();
    }

    boolean activeSubtype(ServiceDocumentType type, String subtype) {
        return jdbc.sql("""
                select count(*) from integration_document_subtypes
                where document_type=:type and code=:code and active=true
                """).param("type", type.name()).param("code", subtype).query(Long.class).single() == 1;
    }

    Optional<Signer> activeSigner(PersonIdentifierScheme scheme, String country, String issuer, String value) {
        return jdbc.sql("""
                select distinct np.id,np.first_name,np.last_name
                from natural_person_identifiers pi
                join natural_persons np on np.id=pi.natural_person_id and np.active=true
                where pi.active=true and pi.scheme=:scheme and pi.issuing_country=:country
                  and upper(pi.issuer)=upper(:issuer) and pi.normalized_value=:value
                  and exists (
                    select 1 from application_users u
                    join application_user_roles ur on ur.user_id=u.id
                    join roles role on role.id=ur.role_id and role.code='SIGNER'
                    where u.natural_person_id=np.id and u.active=true)
                """).param("scheme", scheme.name()).param("country", country)
                .param("issuer", issuer).param("value", value)
                .query((rs, row) -> new Signer(rs.getObject("id", UUID.class),
                        rs.getString("first_name") + " " + rs.getString("last_name"))).optional();
    }

    Optional<StoredRequest> byIdempotency(UUID sourceSystemId, String key) {
        return query(REQUEST_SELECT + " where r.source_system_id=:sourceId and r.idempotency_key=:key")
                .param("sourceId", sourceSystemId).param("key", key).query(this::mapRequest).optional();
    }

    Optional<StoredRequest> byExternalRequestId(UUID sourceSystemId, String externalId) {
        return query(REQUEST_SELECT + " where r.source_system_id=:sourceId and r.external_request_id=:externalId")
                .param("sourceId", sourceSystemId).param("externalId", externalId).query(this::mapRequest).optional();
    }

    Optional<StoredRequest> byId(UUID sourceSystemId, UUID requestId) {
        return query(REQUEST_SELECT + " where r.source_system_id=:sourceId and r.id=:id")
                .param("sourceId", sourceSystemId).param("id", requestId).query(this::mapRequest).optional();
    }

    Optional<StoredRequest> byId(UUID requestId, boolean lock) {
        return query(REQUEST_SELECT + " where r.id=:id" + (lock ? " for update of r" : ""))
                .param("id", requestId).query(this::mapRequest).optional();
    }

    private JdbcClient.StatementSpec query(String sql) { return jdbc.sql(sql); }

    boolean insertRequest(UUID id, String externalId, SourceSystem source, ServiceDocumentType documentType,
                          String subtype, UUID signerId, String idempotencyKey, String fingerprint,
                          String correlationId, String actor) {
        return jdbc.sql("""
                insert into service_signature_requests (
                    id,external_request_id,source_system_id,document_type,document_subtype,
                    signer_natural_person_id,idempotency_key,request_fingerprint,correlation_id,technical_actor)
                values (:id,:externalId,:sourceId,:documentType,:subtype,
                    :signerId,:idempotencyKey,:fingerprint,:correlationId,:actor)
                on conflict do nothing
                """).param("id", id).param("externalId", externalId).param("sourceId", source.id())
                .param("documentType", documentType.name()).param("subtype", subtype).param("signerId", signerId)
                .param("idempotencyKey", idempotencyKey).param("fingerprint", fingerprint)
                .param("correlationId", correlationId).param("actor", actor).update() == 1;
    }

    void insertDocument(UUID id, UUID requestId, ArtifactType type, String sha256, long size,
                        String filename, String mimeType, String objectKey, String pdfaPart,
                        String pdfaConformance, String pdfaValidator, Integer pageCount) {
        jdbc.sql("""
                insert into service_signature_request_documents
                    (id,request_id,artifact_type,sha256,mime_type,size_bytes,original_filename,object_key,
                     pdfa_part,pdfa_conformance,pdfa_validator,page_count)
                values (:id,:requestId,:artifactType,:sha256,:mimeType,:size,:filename,:objectKey,
                        :pdfaPart,:pdfaConformance,:pdfaValidator,:pageCount)
                """).param("id", id).param("requestId", requestId).param("artifactType", type.name())
                .param("sha256", sha256).param("mimeType", mimeType).param("size", size)
                .param("filename", filename).param("objectKey", objectKey).param("pdfaPart", pdfaPart)
                .param("pdfaConformance", pdfaConformance).param("pdfaValidator", pdfaValidator)
                .param("pageCount", pageCount).update();
    }

    boolean markSigned(UUID requestId, long version, OffsetDateTime signedAt, int signatureCount,
                       String signatureValidation) {
        return jdbc.sql("""
                update service_signature_requests set status='SIGNED',signed_at=:signedAt,
                    signature_count=:count,signature_validation=:validation,version=version+1
                where id=:id and version=:version and status in ('PENDING_SIGNATURE','SIGNING')
                """).param("signedAt", signedAt).param("count", signatureCount)
                .param("validation", signatureValidation).param("id", requestId).param("version", version)
                .update() == 1;
    }

    boolean updateConservation(UUID requestId, long version, ServiceConservationStatus status, boolean sent,
                               OffsetDateTime occurredAt, String remoteReference, String errorCode) {
        boolean completed = status == ServiceConservationStatus.ACCEPTED || status == ServiceConservationStatus.REJECTED
                || status == ServiceConservationStatus.FAILED;
        return jdbc.sql("""
                update service_signature_requests set conservation_status=:status,
                    conservation_sent_at=case when :sent then coalesce(conservation_sent_at,:occurredAt)
                                              else conservation_sent_at end,
                    conservation_completed_at=case when :completed then :occurredAt else null end,
                    conservation_remote_reference=coalesce(:reference,conservation_remote_reference),
                    conservation_error_code=:errorCode,version=version+1
                where id=:id and version=:version and status='SIGNED'
                """).param("status", status.name()).param("sent", sent).param("completed", completed)
                .param("occurredAt", occurredAt).param("reference", remoteReference).param("errorCode", errorCode)
                .param("id", requestId).param("version", version).update() == 1;
    }

    Optional<StoredCommand> command(UUID requestId, String key) {
        return jdbc.sql("""
                select command_type,request_fingerprint from service_signature_request_commands
                where request_id=:requestId and idempotency_key=:key
                """).param("requestId", requestId).param("key", key)
                .query((rs, row) -> new StoredCommand(rs.getString("command_type"),
                        rs.getString("request_fingerprint"))).optional();
    }

    void insertCommand(UUID requestId, String key, String type, String fingerprint) {
        jdbc.sql("""
                insert into service_signature_request_commands
                    (request_id,idempotency_key,command_type,request_fingerprint)
                values (:requestId,:key,:type,:fingerprint)
                """).param("requestId", requestId).param("key", key).param("type", type)
                .param("fingerprint", fingerprint).update();
    }

    void insertEvent(UUID requestId, String eventType, String status, String actor, String correlationId,
                     OffsetDateTime occurredAt) {
        jdbc.sql("""
                insert into service_signature_request_events
                    (id,request_id,event_type,status,actor,correlation_id,created_at)
                values (:id,:requestId,:eventType,:status,:actor,:correlationId,:occurredAt)
                """).param("id", UUID.randomUUID()).param("requestId", requestId)
                .param("eventType", eventType).param("status", status).param("actor", actor)
                .param("correlationId", correlationId).param("occurredAt", occurredAt).update();
        String outcome = "SIGNED".equals(status) || "ACCEPTED".equals(status) ? "SUCCESS"
                : "REJECTED".equals(status) || "FAILED".equals(status) ? "FAILURE" : "PENDING";
        jdbc.sql("""
                select append_audit_event(:eventType,'TECHNICAL',:actor,:correlationId,
                    'SIGNATURE_REQUEST',:entityId,:outcome,
                    jsonb_build_object('status',:status),null,:occurredAt)
                """).param("eventType", "SERVICE_" + eventType).param("actor", actor)
                .param("correlationId", correlationId).param("entityId", requestId.toString())
                .param("outcome", outcome).param("status", status).param("occurredAt", occurredAt)
                .query((rs, row) -> 0).single();
    }

    private StoredRequest mapRequest(ResultSet rs, int row) throws SQLException {
        String originalName = rs.getString("original_filename");
        UUID signedId = rs.getObject("signed_id", UUID.class);
        return new StoredRequest(rs.getObject("id", UUID.class), rs.getString("external_request_id"),
                rs.getString("source_system_code"), ServiceDocumentType.valueOf(rs.getString("document_type")),
                rs.getString("document_subtype"), rs.getString("document_subtype_display_name"), rs.getString("status"),
                rs.getObject("signer_natural_person_id", UUID.class),
                rs.getString("signer_first_name") + " " + rs.getString("signer_last_name"),
                new StoredDocument(rs.getObject("original_id", UUID.class), originalName,
                        rs.getString("original_mime_type"), rs.getLong("original_size_bytes"),
                        rs.getString("original_sha256"), rs.getString("original_object_key"), null, null, null, null),
                new StoredDocument(rs.getObject("normalized_id", UUID.class), name(originalName, "-pdfa3.pdf"),
                        "application/pdf", rs.getLong("normalized_size_bytes"), rs.getString("normalized_sha256"),
                        rs.getString("normalized_object_key"), rs.getString("pdfa_part"),
                        rs.getString("pdfa_conformance"), rs.getString("pdfa_validator"), rs.getInt("page_count")),
                signedId == null ? null : new StoredDocument(signedId, name(originalName, "-signed.pdf"),
                        "application/pdf", rs.getLong("signed_size_bytes"), rs.getString("signed_sha256"),
                        rs.getString("signed_object_key"), null, null, null, null),
                rs.getObject("signed_at", OffsetDateTime.class), (Integer) rs.getObject("signature_count"),
                rs.getString("signature_validation"), ServiceConservationStatus.valueOf(rs.getString("conservation_status")),
                rs.getObject("conservation_sent_at", OffsetDateTime.class),
                rs.getObject("conservation_completed_at", OffsetDateTime.class),
                rs.getString("conservation_remote_reference"), rs.getString("conservation_error_code"),
                rs.getString("correlation_id"), rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getString("request_fingerprint"), rs.getString("idempotency_key"), rs.getLong("version"));
    }

    private String name(String original, String suffix) {
        int dot = original.lastIndexOf('.'); return (dot > 0 ? original.substring(0, dot) : original) + suffix;
    }

    enum ArtifactType { ORIGINAL, NORMALIZED_PDFA3, SIGNED }
    record CatalogSubtype(ServiceDocumentType type, String code, String displayName, String sourceReference) {}
    record SourceSystem(UUID id, String code) {}
    record Signer(UUID id, String displayName) {}
    record StoredCommand(String commandType, String fingerprint) {}
    record StoredDocument(UUID id, String filename, String mimeType, long sizeBytes, String sha256,
                          String objectKey, String pdfaPart, String pdfaConformance,
                          String pdfaValidator, Integer pageCount) {}
    record StoredRequest(UUID id, String externalRequestId, String sourceSystemCode,
                         ServiceDocumentType documentType, String documentSubtype,
                         String documentSubtypeDisplayName, String status, UUID signerId,
                         String signerDisplayName, StoredDocument original, StoredDocument normalized,
                         StoredDocument signed, OffsetDateTime signedAt, Integer signatureCount,
                         String signatureValidation, ServiceConservationStatus conservationStatus,
                         OffsetDateTime conservationSentAt, OffsetDateTime conservationCompletedAt,
                         String conservationRemoteReference, String conservationErrorCode,
                         String correlationId, OffsetDateTime submittedAt, String fingerprint,
                         String idempotencyKey, long version) {}
}
