package it.signflow.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.signflow.reports.ReportState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class IngestionRepository {
    private static final String MESSAGE_SELECT = """
            select h.*, r.state report_state from hl7_messages h
            left join reports r on r.id=h.report_id
            """;
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    IngestionRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    Optional<SourceSystemPipeline> sourceSystem(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return jdbc.sql("""
                select id,code,active,pdf_a3_conversion,visible_signature,multiple_signature,
                       send_unsigned,create_cda,passthrough
                from source_systems where upper(code)=upper(:code)
                """).param("code", code.trim()).query((rs, row) -> new SourceSystemPipeline(
                        rs.getObject("id", UUID.class), rs.getString("code"), rs.getBoolean("active"),
                        rs.getBoolean("pdf_a3_conversion"), rs.getBoolean("visible_signature"),
                        rs.getBoolean("multiple_signature"), rs.getBoolean("send_unsigned"),
                        rs.getBoolean("create_cda"), rs.getBoolean("passthrough"))).optional();
    }

    boolean reserve(UUID id, SourceSystemPipeline source, ParsedHl7Message parsed, String idempotencyKey,
                    String deduplicationKey, String payloadHash, String correlationId, long rawSize,
                    OffsetDateTime retentionUntil, IngestionTransport transport, String actor) {
        return jdbc.sql("""
                insert into hl7_messages (
                    id,transport,status,source_system_id,source_system_code,message_type,trigger_event,
                    hl7_version,control_id,idempotency_key,deduplication_key,payload_sha256,
                    correlation_id,raw_size_bytes,raw_retention_until,technical_actor)
                values (:id,:transport,'RECEIVED',:sourceId,:sourceCode,:messageType,:triggerEvent,
                    :version,:controlId,:idempotencyKey,:deduplicationKey,:payloadHash,
                    :correlationId,:rawSize,:retentionUntil,:actor)
                on conflict (deduplication_key) do nothing
                """).param("id", id).param("transport", transport.name())
                .param("sourceId", source == null ? null : source.id())
                .param("sourceCode", parsed == null ? null : parsed.sourceSystemCode())
                .param("messageType", parsed == null ? null : parsed.messageType())
                .param("triggerEvent", parsed == null ? null : parsed.triggerEvent())
                .param("version", parsed == null ? null : parsed.version())
                .param("controlId", parsed == null ? null : parsed.controlId())
                .param("idempotencyKey", idempotencyKey).param("deduplicationKey", deduplicationKey)
                .param("payloadHash", payloadHash).param("correlationId", correlationId)
                .param("rawSize", rawSize).param("retentionUntil", retentionUntil)
                .param("actor", actor).update() == 1;
    }

    UUID insertConflictingDuplicate(UUID id, SourceSystemPipeline source, ParsedHl7Message parsed,
                                    String idempotencyKey, String payloadHash, String correlationId,
                                    long rawSize, OffsetDateTime retentionUntil, IngestionTransport transport,
                                    String actor, UUID duplicateOf) {
        jdbc.sql("""
                insert into hl7_messages (
                    id,transport,status,source_system_id,source_system_code,message_type,trigger_event,
                    hl7_version,control_id,idempotency_key,payload_sha256,correlation_id,raw_size_bytes,
                    raw_retention_until,duplicate_of_id,error_code,error_message,technical_actor,processed_at)
                values (:id,:transport,'DISCARDED',:sourceId,:sourceCode,:messageType,:triggerEvent,
                    :version,:controlId,:idempotencyKey,:payloadHash,:correlationId,:rawSize,
                    :retentionUntil,:duplicateOf,'DUPLICATE_CONFLICT',
                    'Duplicate key or control ID was reused with different content',:actor,now())
                """).param("id", id).param("transport", transport.name())
                .param("sourceId", source == null ? null : source.id())
                .param("sourceCode", parsed == null ? null : parsed.sourceSystemCode())
                .param("messageType", parsed == null ? null : parsed.messageType())
                .param("triggerEvent", parsed == null ? null : parsed.triggerEvent())
                .param("version", parsed == null ? null : parsed.version())
                .param("controlId", parsed == null ? null : parsed.controlId())
                .param("idempotencyKey", idempotencyKey).param("payloadHash", payloadHash)
                .param("correlationId", correlationId).param("rawSize", rawSize)
                .param("retentionUntil", retentionUntil).param("duplicateOf", duplicateOf)
                .param("actor", actor).update();
        return id;
    }

    void rawStored(UUID messageId, String objectKey) {
        jdbc.sql("update hl7_messages set raw_object_key=:objectKey where id=:id")
                .param("objectKey", objectKey).param("id", messageId).update();
    }

    void processed(UUID messageId, UUID reportId, UUID documentId, boolean cdaCalled,
                   boolean normalizerCalled, boolean converterCalled, boolean passthrough,
                   List<String> steps) {
        jdbc.sql("""
                update hl7_messages set status='PROCESSED',report_id=:reportId,document_id=:documentId,
                    cda_builder_called=:cdaCalled,document_normalizer_called=:normalizerCalled,
                    pdf_a3_converter_called=:converterCalled,passthrough_applied=:passthrough,
                    pipeline_steps=cast(:steps as jsonb),processed_at=now()
                where id=:id
                """).param("reportId", reportId).param("documentId", documentId)
                .param("cdaCalled", cdaCalled).param("normalizerCalled", normalizerCalled)
                .param("converterCalled", converterCalled).param("passthrough", passthrough)
                .param("steps", json(steps)).param("id", messageId).update();
    }

    void discarded(UUID messageId, String code, String safeMessage, UUID reportId, List<String> steps) {
        jdbc.sql("""
                update hl7_messages set status='DISCARDED',error_code=:code,error_message=:message,
                    report_id=:reportId,pipeline_steps=cast(:steps as jsonb),processed_at=now()
                where id=:id
                """).param("code", code).param("message", safeMessage).param("reportId", reportId)
                .param("steps", json(steps)).param("id", messageId).update();
    }

    Optional<StoredMessage> byDeduplicationKey(String key) {
        return jdbc.sql(MESSAGE_SELECT + " where h.deduplication_key=:key")
                .param("key", key).query(this::mapStored).optional();
    }

    Optional<StoredMessage> stored(UUID id) {
        return jdbc.sql(MESSAGE_SELECT + " where h.id=:id").param("id", id).query(this::mapStored).optional();
    }

    Optional<UUID> reportByExternalIdentifier(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return jdbc.sql("select id from reports where external_identifier=:value")
                .param("value", value.trim()).query(UUID.class).optional();
    }

    boolean documentTypeExists(String code) {
        return code != null && jdbc.sql("select count(*) from fse_document_types where code=:code and active=true")
                .param("code", code).query(Integer.class).single() > 0;
    }

    UUID practice(String identifier) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into practices (id,practice_identifier,external_reference,description)
                values (:id,:identifier,:identifier,'Pratica HL7 totalmente fittizia')
                on conflict (practice_identifier) do nothing
                """).param("id", id).param("identifier", identifier).update();
        return jdbc.sql("select id from practices where practice_identifier=:identifier")
                .param("identifier", identifier).query(UUID.class).single();
    }

    UUID patient(String identifier, String firstName, String lastName, String fiscalCode, LocalDate birthDate) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into patient_metadata (id,patient_identifier,first_name,last_name,fiscal_code,birth_date)
                values (:id,:identifier,:firstName,:lastName,:fiscalCode,:birthDate)
                on conflict (patient_identifier) do update set
                    first_name=coalesce(nullif(excluded.first_name,''),patient_metadata.first_name),
                    last_name=coalesce(nullif(excluded.last_name,''),patient_metadata.last_name),
                    fiscal_code=coalesce(excluded.fiscal_code,patient_metadata.fiscal_code),
                    birth_date=coalesce(excluded.birth_date,patient_metadata.birth_date)
                """).param("id", id).param("identifier", identifier).param("firstName", firstName)
                .param("lastName", lastName).param("fiscalCode", fiscalCode).param("birthDate", birthDate).update();
        return jdbc.sql("select id from patient_metadata where patient_identifier=:identifier")
                .param("identifier", identifier).query(UUID.class).single();
    }

    Optional<UUID> signer(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        return jdbc.sql("""
                select u.id from application_users u
                join application_user_roles aur on aur.user_id=u.id
                join roles role on role.id=aur.role_id and role.code='SIGNER'
                left join natural_person_identifiers npi on npi.natural_person_id=u.natural_person_id and npi.active=true
                where u.active=true and (upper(u.username)=upper(:identifier)
                    or upper(npi.normalized_value)=upper(:identifier))
                order by case when upper(npi.normalized_value)=upper(:identifier) then 0 else 1 end,
                         u.created_at,u.id limit 1
                """).param("identifier", identifier.trim()).query(UUID.class).optional();
    }

    UUID insertReport(UUID id, String internalIdentifier, String externalIdentifier, UUID practiceId,
                      UUID patientId, UUID signerId, SourceSystemPipeline source, String documentType,
                      String department, OffsetDateTime producedAt) {
        jdbc.sql("""
                insert into reports (
                    id,internal_identifier,external_identifier,practice_id,patient_metadata_id,
                    assigned_signer_id,source_system_id,document_type,department,produced_at,modified_at,
                    pdf_a3_conversion,visible_signature,multiple_signature,send_unsigned,create_cda,passthrough,state)
                values (:id,:internalIdentifier,:externalIdentifier,:practiceId,:patientId,:signerId,
                    :sourceId,:documentType,:department,:producedAt,:producedAt,:pdfA3,:visibleSignature,
                    :multipleSignature,:sendUnsigned,:createCda,:passthrough,'RECEIVED')
                """).param("id", id).param("internalIdentifier", internalIdentifier)
                .param("externalIdentifier", externalIdentifier).param("practiceId", practiceId)
                .param("patientId", patientId).param("signerId", signerId).param("sourceId", source.id())
                .param("documentType", documentType).param("department", department)
                .param("producedAt", producedAt).param("pdfA3", source.pdfA3Conversion())
                .param("visibleSignature", source.visibleSignature()).param("multipleSignature", source.multipleSignature())
                .param("sendUnsigned", source.sendUnsigned()).param("createCda", source.createCda())
                .param("passthrough", source.passthrough()).update();
        return id;
    }

    Hl7MessagePage search(String status, String sourceCode, String messageType, String correlationId,
                          OffsetDateTime from, OffsetDateTime to, int page, int size) {
        int safePage = Math.max(0, page); int safeSize = Math.min(100, Math.max(1, size));
        String filter = """
                where (:status='' or h.status=:status)
                  and (:sourceCode='' or upper(coalesce(h.source_system_code,''))=upper(:sourceCode))
                  and (:messageType='' or h.message_type=:messageType)
                  and (:correlationId='' or h.correlation_id=:correlationId)
                  and (cast(:receivedFrom as timestamptz) is null or h.received_at>=cast(:receivedFrom as timestamptz))
                  and (cast(:receivedTo as timestamptz) is null or h.received_at<=cast(:receivedTo as timestamptz))
                """;
        long total = bind(jdbc.sql("select count(*) from hl7_messages h " + filter), status, sourceCode,
                messageType, correlationId, from, to).query(Long.class).single();
        List<Hl7MessageSummary> items = bind(jdbc.sql(MESSAGE_SELECT + filter
                        + " order by h.received_at desc,h.id desc limit :limit offset :offset"), status,
                sourceCode, messageType, correlationId, from, to).param("limit", safeSize)
                .param("offset", safePage * safeSize).query((rs, row) -> mapStored(rs, row).summary()).list();
        return new Hl7MessagePage(items, safePage, safeSize, total);
    }

    MissingSignerPage missingSigners(int page, int size) {
        int safePage = Math.max(0, page); int safeSize = Math.min(100, Math.max(1, size));
        long total = jdbc.sql("select count(*) from reports where state='MISSING_SIGNER'").query(Long.class).single();
        List<MissingSignerReport> items = jdbc.sql("""
                select r.id,r.internal_identifier,s.code source_system_code,r.document_type,r.department,r.produced_at
                from reports r join source_systems s on s.id=r.source_system_id
                where r.state='MISSING_SIGNER' order by r.produced_at desc,r.id limit :limit offset :offset
                """).param("limit", safeSize).param("offset", safePage * safeSize)
                .query((rs, row) -> new MissingSignerReport(rs.getObject("id", UUID.class),
                        rs.getString("internal_identifier"), rs.getString("source_system_code"),
                        rs.getString("document_type"), rs.getString("department"),
                        rs.getObject("produced_at", OffsetDateTime.class))).list();
        return new MissingSignerPage(items, safePage, safeSize, total);
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, String status, String sourceCode,
                                          String messageType, String correlationId,
                                          OffsetDateTime from, OffsetDateTime to) {
        return spec.param("status", normalized(status)).param("sourceCode", normalized(sourceCode))
                .param("messageType", normalized(messageType).toUpperCase())
                .param("correlationId", normalized(correlationId)).param("receivedFrom", from).param("receivedTo", to);
    }

    private StoredMessage mapStored(ResultSet rs, int row) throws SQLException {
        ReportState reportState = rs.getString("report_state") == null ? null : ReportState.valueOf(rs.getString("report_state"));
        Hl7MessageSummary summary = new Hl7MessageSummary(rs.getObject("id", UUID.class),
                rs.getObject("received_at", OffsetDateTime.class), rs.getObject("processed_at", OffsetDateTime.class),
                rs.getString("transport"), rs.getString("status"), rs.getString("source_system_code"),
                rs.getString("message_type"), rs.getString("trigger_event"), rs.getString("control_id"),
                rs.getString("correlation_id"), rs.getObject("report_id", UUID.class),
                rs.getObject("document_id", UUID.class), rs.getString("error_code"), rs.getString("error_message"));
        return new StoredMessage(summary, rs.getString("hl7_version"), rs.getString("payload_sha256"),
                rs.getLong("raw_size_bytes"), rs.getObject("raw_retention_until", OffsetDateTime.class),
                rs.getString("raw_object_key"), rs.getBoolean("cda_builder_called"),
                rs.getBoolean("document_normalizer_called"), rs.getBoolean("pdf_a3_converter_called"),
                rs.getBoolean("passthrough_applied"), readSteps(rs.getString("pipeline_steps")),
                rs.getObject("duplicate_of_id", UUID.class), reportState);
    }

    private List<String> readSteps(String value) throws SQLException {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new SQLException("Invalid pipeline step metadata", exception); }
    }

    private String json(List<String> steps) {
        try { return objectMapper.writeValueAsString(steps == null ? List.of() : steps); }
        catch (Exception exception) { throw new IllegalArgumentException("Pipeline steps cannot be serialized", exception); }
    }

    private String normalized(String value) { return value == null ? "" : value.trim(); }

    record StoredMessage(Hl7MessageSummary summary, String version, String payloadHash, long rawSize,
                         OffsetDateTime retentionUntil, String rawObjectKey, boolean cdaCalled,
                         boolean normalizerCalled, boolean converterCalled, boolean passthrough,
                         List<String> steps, UUID duplicateOfId, ReportState reportState) {
        IngestionResult result(boolean idempotent) {
            return new IngestionResult(summary.id(), Hl7MessageStatus.valueOf(summary.status()), summary.reportId(),
                    summary.documentId(), reportState, summary.correlationId(), summary.errorCode(),
                    summary.errorMessage(), idempotent);
        }
    }
}
