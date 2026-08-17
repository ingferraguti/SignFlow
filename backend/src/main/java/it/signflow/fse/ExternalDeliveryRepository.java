package it.signflow.fse;

import static it.signflow.fse.ExternalDeliveryModels.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ExternalDeliveryRepository {
    private final JdbcClient jdbc;
    ExternalDeliveryRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    Optional<Preparation> preparation(UUID reportId) {
        return jdbc.sql("""
                select r.id report_id, r.internal_identifier, r.state report_state, r.workflow_version,
                       r.signature_kind, r.document_type, r.department, s.code source_system_code,
                       d.id document_id, d.object_key, d.sha256,
                       m.facility_code, m.facility_name, m.operating_unit
                from reports r join source_systems s on s.id=r.source_system_id
                left join lateral (
                    select id,object_key,sha256 from clinical_documents
                    where report_id=r.id and status='ACTIVE' order by version desc limit 1
                ) d on true
                left join lateral (
                    select facility_code,facility_name,operating_unit from fse_facility_mappings
                    where source_system_id=r.source_system_id and department=r.department and active=true
                    order by updated_at desc limit 1
                ) m on true
                where r.id=:reportId
                """).param("reportId", reportId).query(this::mapPreparation).optional();
    }

    Optional<StoredOperation> find(UUID id, boolean lock) {
        String suffix = lock ? " for update of o" : "";
        return jdbc.sql("""
                select o.*, r.internal_identifier report_identifier, r.workflow_version report_version,
                       s.code source_system_code, d.sha256
                from external_delivery_operations o join reports r on r.id=o.report_id
                join source_systems s on s.id=r.source_system_id
                left join clinical_documents d on d.id=o.document_id
                where o.id=:id
                """ + suffix).param("id", id).query(this::mapStoredOperation).optional();
    }

    Optional<StoredOperation> findByReportAndChannel(UUID reportId, Channel channel) {
        return jdbc.sql("""
                select o.*, r.internal_identifier report_identifier, r.workflow_version report_version,
                       s.code source_system_code, d.sha256
                from external_delivery_operations o join reports r on r.id=o.report_id
                join source_systems s on s.id=r.source_system_id
                left join clinical_documents d on d.id=o.document_id
                where o.report_id=:reportId and o.channel=:channel
                """).param("reportId", reportId).param("channel", channel.name())
                .query(this::mapStoredOperation).optional();
    }

    OperationPage search(String channel, String state, String correlationId, String reportIdentifier,
                         int page, int size) {
        int safePage = Math.max(0, page), safeSize = Math.min(100, Math.max(1, size)), offset = safePage * safeSize;
        String where = """
                where (:channel='' or o.channel=:channel) and (:state='' or o.state=:state)
                  and (:correlationId='' or lower(o.correlation_id) like lower('%' || :correlationId || '%'))
                  and (:reportIdentifier='' or lower(r.internal_identifier) like lower('%' || :reportIdentifier || '%'))
                """;
        long total = bind(jdbc.sql("select count(*) from external_delivery_operations o join reports r on r.id=o.report_id " + where),
                channel, state, correlationId, reportIdentifier).query(Long.class).single();
        List<OperationSummary> items = bind(jdbc.sql("""
                select o.*, r.internal_identifier report_identifier, r.workflow_version report_version,
                       s.code source_system_code, d.sha256
                from external_delivery_operations o join reports r on r.id=o.report_id
                join source_systems s on s.id=r.source_system_id
                left join clinical_documents d on d.id=o.document_id
                """ + where + " order by o.updated_at desc,o.id limit :limit offset :offset"),
                channel, state, correlationId, reportIdentifier)
                .param("limit", safeSize).param("offset", offset).query((rs, row) -> mapStoredOperation(rs, row).summary()).list();
        return new OperationPage(items, safePage, safeSize, total);
    }

    StoredOperation insert(UUID id, Preparation p, Channel channel, State state, String correlationId,
                           String adapterCode, String actor, int maxRetries, String errorCode, String errorMessage) {
        jdbc.sql("""
                insert into external_delivery_operations
                    (id,report_id,document_id,channel,state,correlation_id,adapter_code,facility_code,
                     facility_name,operating_unit,department,document_type_code,attempt_count,max_retries,
                     error_code,error_message,created_by)
                values (:id,:reportId,:documentId,:channel,:state,:correlationId,:adapterCode,:facilityCode,
                        :facilityName,:operatingUnit,:department,:documentTypeCode,0,:maxRetries,
                        :errorCode,:errorMessage,:actor)
                """).param("id", id).param("reportId", p.reportId()).param("documentId", p.documentId())
                .param("channel", channel.name()).param("state", state.name()).param("correlationId", correlationId)
                .param("adapterCode", adapterCode).param("facilityCode", fallback(p.facilityCode(), "UNMAPPED"))
                .param("facilityName", fallback(p.facilityName(), "Mapping presidio assente"))
                .param("operatingUnit", fallback(p.operatingUnit(), "UNMAPPED"))
                .param("department", fallback(p.department(), "UNMAPPED"))
                .param("documentTypeCode", p.documentTypeCode()).param("maxRetries", maxRetries)
                .param("errorCode", errorCode).param("errorMessage", errorMessage).param("actor", actor).update();
        return find(id, false).orElseThrow();
    }

    boolean update(UUID id, long expectedVersion, State state, String remoteReference,
                   boolean incrementAttempt, String errorCode, String errorMessage) {
        return jdbc.sql("""
                update external_delivery_operations set state=:state, remote_reference=coalesce(:remoteReference,remote_reference),
                    attempt_count=attempt_count + case when :incrementAttempt then 1 else 0 end,
                    error_code=:errorCode,error_message=:errorMessage,version=version+1,updated_at=now(),
                    sent_at=case when :state in ('FSE_SENT','CONSERVATION_SENT') then now() else sent_at end,
                    completed_at=case when :state in ('FSE_ACCEPTED','FSE_REJECTED','CONSERVATION_ACCEPTED','CONSERVATION_REJECTED') then now() else completed_at end,
                    reconciled_at=case when :incrementAttempt then reconciled_at else now() end
                where id=:id and version=:expectedVersion
                """).param("id", id).param("expectedVersion", expectedVersion).param("state", state.name())
                .param("remoteReference", remoteReference).param("incrementAttempt", incrementAttempt)
                .param("errorCode", errorCode).param("errorMessage", errorMessage).update() == 1;
    }

    AttemptResponse insertAttempt(UUID id, UUID operationId, int number, String action, String outcome,
                                  String correlationId, String remoteReference, String errorCode, String errorMessage) {
        return jdbc.sql("""
                insert into external_delivery_attempts
                    (id,operation_id,attempt_number,action,outcome,correlation_id,remote_reference,error_code,error_message)
                values (:id,:operationId,:number,:action,:outcome,:correlationId,:remoteReference,:errorCode,:errorMessage)
                returning *
                """).param("id", id).param("operationId", operationId).param("number", number)
                .param("action", action).param("outcome", outcome).param("correlationId", correlationId)
                .param("remoteReference", remoteReference).param("errorCode", errorCode)
                .param("errorMessage", errorMessage).query(this::mapAttempt).single();
    }

    void insertReceipt(UUID id, UUID operationId, UUID attemptId, String type, String key,
                       String hash, String mimeType, long size) {
        jdbc.sql("""
                insert into external_delivery_receipts
                    (id,operation_id,attempt_id,receipt_type,object_key,sha256,mime_type,size_bytes)
                values (:id,:operationId,:attemptId,:type,:key,:hash,:mimeType,:size)
                """).param("id", id).param("operationId", operationId).param("attemptId", attemptId)
                .param("type", type).param("key", key).param("hash", hash).param("mimeType", mimeType)
                .param("size", size).update();
    }

    List<AttemptResponse> attempts(UUID operationId) {
        return jdbc.sql("select * from external_delivery_attempts where operation_id=:id order by started_at,id")
                .param("id", operationId).query(this::mapAttempt).list();
    }

    List<ReceiptResponse> receipts(UUID operationId) {
        return jdbc.sql("select * from external_delivery_receipts where operation_id=:id order by created_at,id")
                .param("id", operationId).query((rs, row) -> new ReceiptResponse(
                        rs.getObject("id", UUID.class), rs.getObject("attempt_id", UUID.class),
                        rs.getString("receipt_type"), rs.getString("mime_type"), rs.getLong("size_bytes"),
                        rs.getString("sha256"), rs.getObject("created_at", OffsetDateTime.class),
                        "/api/backend/admin/external-deliveries/receipts/" + rs.getObject("id", UUID.class) + "/download")).list();
    }

    Optional<StoredReceipt> receipt(UUID id) {
        return jdbc.sql("select * from external_delivery_receipts where id=:id").param("id", id)
                .query((rs, row) -> new StoredReceipt(rs.getObject("id", UUID.class), rs.getString("receipt_type"),
                        rs.getString("object_key"), rs.getString("mime_type"))).optional();
    }

    Optional<String> documentObjectKey(UUID documentId) {
        if (documentId == null) return Optional.empty();
        return jdbc.sql("select object_key from clinical_documents where id=:id")
                .param("id", documentId).query(String.class).optional();
    }

    Optional<StoredCommand> command(UUID operationId, String key) {
        return jdbc.sql("select * from external_delivery_commands where operation_id=:id and command_key=:key")
                .param("id", operationId).param("key", key).query((rs, row) -> new StoredCommand(
                        rs.getString("request_fingerprint"), State.valueOf(rs.getString("resulting_state")))).optional();
    }

    void insertCommand(UUID operationId, String key, String type, String fingerprint, State state) {
        jdbc.sql("""
                insert into external_delivery_commands (id,operation_id,command_key,command_type,request_fingerprint,resulting_state)
                values (:id,:operationId,:key,:type,:fingerprint,:state)
                """).param("id", UUID.randomUUID()).param("operationId", operationId).param("key", key)
                .param("type", type).param("fingerprint", fingerprint).param("state", state.name()).update();
    }

    int reconciliationCount(UUID operationId) {
        return jdbc.sql("select count(*) from external_delivery_attempts where operation_id=:id and action='RECONCILE'")
                .param("id", operationId).query(Integer.class).single();
    }

    int eventCount(UUID operationId) {
        return jdbc.sql("select count(*) from external_delivery_attempts where operation_id=:id")
                .param("id", operationId).query(Integer.class).single();
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, String channel, String state,
                                          String correlationId, String reportIdentifier) {
        return statement.param("channel", normalize(channel)).param("state", normalize(state))
                .param("correlationId", normalize(correlationId)).param("reportIdentifier", normalize(reportIdentifier));
    }

    private Preparation mapPreparation(ResultSet rs, int row) throws SQLException {
        return new Preparation(rs.getObject("report_id", UUID.class), rs.getString("internal_identifier"),
                rs.getString("report_state"), rs.getLong("workflow_version"), rs.getString("signature_kind"),
                rs.getString("document_type"), rs.getString("department"), rs.getString("source_system_code"),
                rs.getObject("document_id", UUID.class), rs.getString("object_key"), rs.getString("sha256"),
                rs.getString("facility_code"), rs.getString("facility_name"), rs.getString("operating_unit"));
    }

    private StoredOperation mapStoredOperation(ResultSet rs, int row) throws SQLException {
        OperationSummary summary = new OperationSummary(rs.getObject("id", UUID.class), rs.getObject("report_id", UUID.class),
                rs.getString("report_identifier"), rs.getObject("document_id", UUID.class), Channel.valueOf(rs.getString("channel")),
                State.valueOf(rs.getString("state")), rs.getString("correlation_id"), rs.getString("adapter_code"),
                rs.getString("facility_code"), rs.getString("facility_name"), rs.getString("operating_unit"),
                rs.getString("department"), rs.getString("document_type_code"), rs.getString("remote_reference"),
                rs.getInt("attempt_count"), rs.getInt("max_retries"), rs.getLong("version"), rs.getLong("report_version"),
                rs.getString("error_code"), rs.getString("error_message"), rs.getString("created_by"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("sent_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class), rs.getObject("reconciled_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
        DeliveryMetadata metadata = new DeliveryMetadata(summary.reportIdentifier(), summary.documentTypeCode(),
                summary.facilityCode(), summary.facilityName(), summary.operatingUnit(), summary.department(),
                rs.getString("source_system_code"), rs.getString("sha256"));
        return new StoredOperation(summary, metadata);
    }

    private AttemptResponse mapAttempt(ResultSet rs, int row) throws SQLException {
        return new AttemptResponse(rs.getObject("id", UUID.class), rs.getInt("attempt_number"), rs.getString("action"),
                rs.getString("outcome"), rs.getString("correlation_id"), rs.getString("remote_reference"),
                rs.getString("error_code"), rs.getString("error_message"),
                rs.getObject("started_at", OffsetDateTime.class), rs.getObject("completed_at", OffsetDateTime.class));
    }

    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    record Preparation(UUID reportId, String reportIdentifier, String reportState, long workflowVersion,
                       String signatureKind, String documentTypeCode, String department, String sourceSystemCode,
                       UUID documentId, String objectKey, String sha256, String facilityCode,
                       String facilityName, String operatingUnit) {}
    record StoredOperation(OperationSummary summary, DeliveryMetadata metadata) {}
    record StoredReceipt(UUID id, String type, String objectKey, String mimeType) {}
    record StoredCommand(String fingerprint, State resultingState) {}
}
