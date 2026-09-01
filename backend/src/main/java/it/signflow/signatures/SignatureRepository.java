package it.signflow.signatures;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SignatureRepository {
    private static final String VISIBLE = """
            exists (
                select 1 from application_users me
                join natural_persons np on np.id=me.natural_person_id and np.active=true
                join application_users owner on owner.id=r.assigned_signer_id
                where me.username=:username and me.active=true
                  and owner.natural_person_id=me.natural_person_id
            )
            """;
    private final JdbcClient jdbc;

    SignatureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<SignerAccount> account(String username, UUID requestedAccountId) {
        return jdbc.sql("""
                select sa.id account_id, sa.account_alias, sp.code provider_code, sp.adapter_type
                from signature_accounts sa
                join application_users u on u.natural_person_id=sa.natural_person_id
                join signature_providers sp on sp.id=sa.signature_provider_id
                where u.username=:username and u.active=true and sa.active=true and sp.active=true
                  and (:requested=false or sa.id=:accountId)
                order by (sa.id=u.preferred_signature_account_id) desc, sa.display_name limit 1
                """).param("username", username).param("requested", requestedAccountId != null)
                .param("accountId", requestedAccountId == null ? new UUID(0, 0) : requestedAccountId)
                .query((rs, row) -> new SignerAccount(
                rs.getObject("account_id", UUID.class), rs.getString("account_alias"),
                rs.getString("provider_code"), rs.getString("adapter_type"))).optional();
    }

    ProviderSessionResponse insertSession(UUID id, SignerAccount account, String username, OffsetDateTime expiresAt,
                                          String providerSessionReference, String challengeReference,
                                          String correlationId) {
        jdbc.sql("""
                insert into provider_sessions
                    (id, signature_account_id, actor_username, natural_person_id, provider_code, state, expires_at,
                     provider_session_reference, challenge_reference, correlation_id)
                select :id, :accountId, :username, u.natural_person_id, :providerCode, 'ACTIVE', :expiresAt,
                        :providerSessionReference, :challengeReference, :correlationId
                from application_users u where u.username=:username
                """).param("id", id).param("accountId", account.id()).param("username", username)
                .param("providerCode", account.providerCode()).param("expiresAt", expiresAt)
                .param("providerSessionReference", providerSessionReference)
                .param("challengeReference", challengeReference).param("correlationId", correlationId).update();
        return new ProviderSessionResponse(id, account.providerCode(), "ACTIVE", expiresAt,
                MockSignatureProvider.MOCK_NOTICE);
    }

    Optional<SessionData> session(UUID id, String username) {
        return jdbc.sql("""
                select ps.id, ps.signature_account_id, ps.provider_code, ps.expires_at, ps.state,
                       ps.provider_session_reference, ps.challenge_reference, ps.correlation_id,
                       sa.account_alias, sp.adapter_type
                from provider_sessions ps
                join signature_accounts sa on sa.id=ps.signature_account_id
                join signature_providers sp on sp.id=sa.signature_provider_id
                where ps.id=:id and ps.natural_person_id=(
                    select natural_person_id from application_users where username=:username and active=true)
                """).param("id", id).param("username", username).query((rs, row) -> new SessionData(
                rs.getObject("id", UUID.class), rs.getObject("signature_account_id", UUID.class),
                rs.getString("provider_code"), rs.getString("account_alias"), rs.getString("adapter_type"),
                rs.getString("state"), rs.getObject("expires_at", OffsetDateTime.class),
                rs.getString("provider_session_reference"), rs.getString("challenge_reference"),
                rs.getString("correlation_id"))).optional();
    }

    List<EligibleReport> eligible(String username, SignatureSelectionMode mode, List<UUID> reportIds,
                                  SignatureSelectionFilter filters) {
        StringBuilder where = new StringBuilder(" where ").append(VISIBLE)
                .append(" and dt.active and exists (select 1 from clinical_documents cd where cd.report_id=r.id and cd.status='ACTIVE')")
                .append(" and ((dt.approval_required and r.state='APPROVED')")
                .append(" or (not dt.approval_required and dt.preview_required and r.state='PREVIEWED')")
                .append(" or (not dt.approval_required and not dt.preview_required and r.state in ('RECEIVED','PARSED','PREVIEWED'))) ");
        Map<String, Object> params = new HashMap<>();
        params.put("username", username);
        if (mode != SignatureSelectionMode.FILTERED) {
            where.append(" and r.id in (:reportIds)");
            params.put("reportIds", reportIds);
        } else if (filters != null) {
            addFilter(where, params, "query", filters.query(),
                    "(lower(r.internal_identifier) like :query or lower(pm.first_name) like :query or lower(pm.last_name) like :query or lower(r.document_type) like :query or lower(r.department) like :query)");
            addFilter(where, params, "patient", filters.patient(),
                    "(lower(pm.patient_identifier) like :patient or lower(pm.first_name) like :patient or lower(pm.last_name) like :patient)");
            addFilter(where, params, "documentType", filters.documentType(), "lower(r.document_type) like :documentType");
            addFilter(where, params, "department", filters.department(), "lower(r.department) like :department");
        }
        return jdbc.sql("""
                select r.id, r.internal_identifier, r.workflow_version,
                       coalesce(ms.failures_before_success, 0) failures_before_success
                from reports r join patient_metadata pm on pm.id=r.patient_metadata_id
                join fse_document_types dt on dt.code=r.document_type
                left join mock_signature_scenarios ms on ms.report_id=r.id
                """ + where + " order by r.produced_at, r.id")
                .params(params).query((rs, row) -> new EligibleReport(rs.getObject("id", UUID.class),
                        rs.getString("internal_identifier"), rs.getLong("workflow_version"),
                        rs.getInt("failures_before_success"))).list();
    }

    private void addFilter(StringBuilder where, Map<String, Object> params, String name, String value,
                           String expression) {
        if (value != null && !value.isBlank()) {
            where.append(" and ").append(expression);
            params.put(name, "%" + value.trim().toLowerCase() + "%");
        }
    }

    void insertBatch(UUID id, String username, SignerAccount account, SignatureSelectionMode mode,
                     String filterSnapshot, String operationKey, String fingerprint,
                     List<EligibleReport> reports) {
        jdbc.sql("""
                insert into signature_batches
                    (id, signer_username, signer_natural_person_id, signature_account_id, selection_mode, filter_snapshot, state,
                     create_operation_key, create_fingerprint, total_count)
                select :id, :username, u.natural_person_id, :accountId, :mode, :filterSnapshot, 'DRAFT',
                        :operationKey, :fingerprint, :total
                from application_users u where u.username=:username
                """).param("id", id).param("username", username).param("accountId", account.id())
                .param("mode", mode.name()).param("filterSnapshot", filterSnapshot)
                .param("operationKey", operationKey).param("fingerprint", fingerprint)
                .param("total", reports.size()).update();
        for (EligibleReport report : reports) {
            jdbc.sql("""
                    insert into signature_attempts (id, batch_id, report_id, state)
                    values (:id, :batchId, :reportId, 'PENDING')
                    """).param("id", UUID.randomUUID()).param("batchId", id)
                    .param("reportId", report.id()).update();
        }
    }

    Optional<StoredCreate> findCreate(String username, String operationKey) {
        return jdbc.sql("""
                select b.id, b.create_fingerprint from signature_batches b
                join application_users u on u.natural_person_id=b.signer_natural_person_id
                where u.username=:username and u.active=true and b.create_operation_key=:operationKey
                """).param("username", username).param("operationKey", operationKey)
                .query((rs, row) -> new StoredCreate(rs.getObject("id", UUID.class),
                        rs.getString("create_fingerprint"))).optional();
    }

    void lock(UUID batchId) {
        jdbc.sql("select id from signature_batches where id=:id for update")
                .param("id", batchId).query(UUID.class).optional();
    }

    Optional<BatchData> batch(UUID batchId, String username) {
        return jdbc.sql("""
                select b.*, sp.code provider_code
                from signature_batches b
                join signature_accounts sa on sa.id=b.signature_account_id
                join signature_providers sp on sp.id=sa.signature_provider_id
                where b.id=:id and b.signer_natural_person_id=(
                    select natural_person_id from application_users where username=:username and active=true)
                """).param("id", batchId).param("username", username).query(this::mapBatch).optional();
    }

    List<BatchData> batches(String username) {
        return jdbc.sql("""
                select b.*, sp.code provider_code
                from signature_batches b
                join signature_accounts sa on sa.id=b.signature_account_id
                join signature_providers sp on sp.id=sa.signature_provider_id
                where b.signer_natural_person_id=(
                    select natural_person_id from application_users where username=:username and active=true)
                order by b.created_at desc limit 50
                """).param("username", username).query(this::mapBatch).list();
    }

    List<AttemptData> attempts(UUID batchId) {
        return jdbc.sql("""
                select a.*, r.internal_identifier, r.state report_state, r.workflow_version,
                       coalesce(ms.failures_before_success,0) failures_before_success
                from signature_attempts a join reports r on r.id=a.report_id
                left join mock_signature_scenarios ms on ms.report_id=r.id
                where a.batch_id=:batchId order by r.internal_identifier
                """).param("batchId", batchId).query(this::mapAttempt).list();
    }

    Optional<AttemptData> attempt(UUID batchId, UUID attemptId) {
        return jdbc.sql("""
                select a.*, r.internal_identifier, r.state report_state, r.workflow_version,
                       coalesce(ms.failures_before_success,0) failures_before_success
                from signature_attempts a join reports r on r.id=a.report_id
                left join mock_signature_scenarios ms on ms.report_id=r.id
                where a.batch_id=:batchId and a.id=:attemptId
                """).param("batchId", batchId).param("attemptId", attemptId).query(this::mapAttempt).optional();
    }

    Optional<StoredOperation> operation(UUID batchId, String operationKey) {
        return jdbc.sql("""
                select operation_type, request_fingerprint from signature_batch_operations
                where batch_id=:batchId and operation_key=:operationKey
                """).param("batchId", batchId).param("operationKey", operationKey)
                .query((rs, row) -> new StoredOperation(rs.getString("operation_type"),
                        rs.getString("request_fingerprint"))).optional();
    }

    void insertOperation(UUID batchId, UUID attemptId, String key, String type, String fingerprint) {
        jdbc.sql("""
                insert into signature_batch_operations
                    (id, batch_id, attempt_id, operation_key, operation_type, request_fingerprint)
                values (:id, :batchId, :attemptId, :key, :type, :fingerprint)
                """).param("id", UUID.randomUUID()).param("batchId", batchId).param("attemptId", attemptId)
                .param("key", key).param("type", type).param("fingerprint", fingerprint).update();
    }

    void confirm(UUID batchId, UUID sessionId) {
        jdbc.sql("""
                update signature_batches set state='CONFIRMED', provider_session_id=:sessionId,
                    confirmed_at=now(), version=version+1 where id=:id
                """).param("sessionId", sessionId).param("id", batchId).update();
    }

    void running(UUID batchId) {
        jdbc.sql("update signature_batches set state='RUNNING', started_at=now(), version=version+1 where id=:id")
                .param("id", batchId).update();
    }

    void markSigning(UUID attemptId, boolean retry) {
        jdbc.sql("""
                update signature_attempts set state='SIGNING', started_at=now(), completed_at=null,
                    error_code=null, error_message=null,
                    retry_count=retry_count + case when :retry then 1 else 0 end
                where id=:id
                """).param("retry", retry).param("id", attemptId).update();
    }

    void succeed(UUID attemptId, ProviderArtifact result) {
        jdbc.sql("""
                update signature_attempts set state='SUCCEEDED', provider_reference=:reference,
                    artifact_id=:artifactId, artifact_name=:artifactName,
                    artifact_notice=:notice, artifact_content=:content,
                    error_code=null, error_message=null, completed_at=now()
                where id=:id
                """).param("reference", result.providerReference()).param("artifactId", UUID.randomUUID())
                .param("artifactName", result.artifactName()).param("notice", result.notice())
                .param("content", result.artifactContent()).param("id", attemptId).update();
    }

    void fail(UUID attemptId, ProviderFailure result) {
        jdbc.sql("""
                update signature_attempts set state='FAILED', error_code=:code,
                    error_message=:message, completed_at=now() where id=:id
                """).param("code", result.errorCode()).param("message", result.errorMessage())
                .param("id", attemptId).update();
    }

    void finalizeBatch(UUID batchId) {
        Map<String, Integer> counts = jdbc.sql("""
                select count(*) filter (where state='SUCCEEDED') success,
                       count(*) filter (where state='FAILED') failure,
                       count(*) total from signature_attempts where batch_id=:batchId
                """).param("batchId", batchId).query((rs, row) -> Map.of(
                        "success", rs.getInt("success"), "failure", rs.getInt("failure"),
                        "total", rs.getInt("total"))).single();
        String state = counts.get("success") == counts.get("total") ? "COMPLETED"
                : counts.get("success") > 0 ? "PARTIAL_SUCCESS" : "FAILED";
        jdbc.sql("""
                update signature_batches set state=:state, success_count=:success,
                    failure_count=:failure, completed_at=now(), version=version+1 where id=:id
                """).param("state", state).param("success", counts.get("success"))
                .param("failure", counts.get("failure")).param("id", batchId).update();
    }

    void cancel(UUID batchId) {
        jdbc.sql("update signature_attempts set state='CANCELLED', completed_at=now() where batch_id=:id and state='PENDING'")
                .param("id", batchId).update();
        jdbc.sql("""
                update signature_batches set state='CANCELLED', cancelled_at=now(),
                    completed_at=now(), version=version+1 where id=:id
                """).param("id", batchId).update();
    }

    Optional<SignatureArtifact> artifact(UUID artifactId, String username) {
        return jdbc.sql("""
                select a.artifact_name, a.artifact_content
                from signature_attempts a join signature_batches b on b.id=a.batch_id
                where a.artifact_id=:artifactId and a.state='SUCCEEDED'
                  and b.signer_natural_person_id=(
                    select natural_person_id from application_users where username=:username and active=true)
                """).param("artifactId", artifactId).param("username", username)
                .query((rs, row) -> new SignatureArtifact(rs.getString("artifact_name"),
                        rs.getString("artifact_content"))).optional();
    }

    private BatchData mapBatch(ResultSet rs, int row) throws SQLException {
        return new BatchData(rs.getObject("id", UUID.class), rs.getString("signer_username"),
                rs.getString("provider_code"), SignatureSelectionMode.valueOf(rs.getString("selection_mode")),
                rs.getString("filter_snapshot"), SignatureBatchState.valueOf(rs.getString("state")),
                rs.getLong("version"), rs.getInt("total_count"), rs.getInt("success_count"),
                rs.getInt("failure_count"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("confirmed_at", OffsetDateTime.class), rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class), rs.getObject("cancelled_at", OffsetDateTime.class));
    }

    private AttemptData mapAttempt(ResultSet rs, int row) throws SQLException {
        return new AttemptData(rs.getObject("id", UUID.class), rs.getObject("report_id", UUID.class),
                rs.getString("internal_identifier"), SignatureAttemptState.valueOf(rs.getString("state")),
                rs.getInt("retry_count"), rs.getInt("max_retries"), rs.getString("provider_reference"),
                rs.getObject("artifact_id", UUID.class), rs.getString("artifact_name"),
                rs.getString("artifact_notice"), rs.getString("error_code"), rs.getString("error_message"),
                rs.getObject("started_at", OffsetDateTime.class), rs.getObject("completed_at", OffsetDateTime.class),
                rs.getString("report_state"), rs.getLong("workflow_version"),
                rs.getInt("failures_before_success"));
    }

    record SignerAccount(UUID id, String accountAlias, String providerCode, String adapterType) {}
    record SessionData(UUID id, UUID accountId, String providerCode, String accountAlias, String adapterType,
                       String state, OffsetDateTime expiresAt, String providerSessionReference,
                       String challengeReference, String correlationId) {}
    record EligibleReport(UUID id, String identifier, long workflowVersion, int failuresBeforeSuccess) {}
    record StoredCreate(UUID batchId, String fingerprint) {}
    record StoredOperation(String type, String fingerprint) {}
    record ProviderArtifact(String providerReference, String artifactName, String artifactContent, String notice) {}
    record ProviderFailure(String errorCode, String errorMessage) {}
    record BatchData(UUID id, String signerUsername, String providerCode, SignatureSelectionMode selectionMode,
                     String filterSnapshot, SignatureBatchState state, long version, int totalCount,
                     int successCount, int failureCount, OffsetDateTime createdAt, OffsetDateTime confirmedAt,
                     OffsetDateTime startedAt, OffsetDateTime completedAt, OffsetDateTime cancelledAt) {}
    record AttemptData(UUID id, UUID reportId, String reportIdentifier, SignatureAttemptState state,
                       int retryCount, int maxRetries, String providerReference, UUID artifactId,
                       String artifactName, String artifactNotice, String errorCode, String errorMessage,
                       OffsetDateTime startedAt, OffsetDateTime completedAt, String reportState,
                       long workflowVersion, int failuresBeforeSuccess) {}
}
