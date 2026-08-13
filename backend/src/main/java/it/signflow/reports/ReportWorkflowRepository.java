package it.signflow.reports;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ReportWorkflowRepository {
    private final JdbcClient jdbcClient;

    ReportWorkflowRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Optional<ReportWorkflowSnapshot> findSnapshot(UUID reportId) {
        return jdbcClient.sql("""
                select r.id, r.state, r.workflow_version, r.assigned_signer_id, r.first_previewed_at,
                       assigned.username signer_username, coalesce(assigned.active, false) signer_active,
                        identifier.normalized_value signer_fiscal_code,
                       exists (
                           select 1 from application_user_roles aur
                           join roles role on role.id=aur.role_id
                           where aur.user_id=assigned.id and role.code='SIGNER'
                       ) signer_role,
                       pr.practice_identifier, pm.patient_identifier, pm.first_name patient_first_name,
                       pm.last_name patient_last_name, pm.fiscal_code patient_fiscal_code,
                       r.document_type, r.department, r.produced_at, ss.active source_system_active,
                       (select count(*) from clinical_documents cd
                        where cd.report_id=r.id and cd.status='ACTIVE') active_document_count
                from reports r
                join practices pr on pr.id=r.practice_id
                join patient_metadata pm on pm.id=r.patient_metadata_id
                join source_systems ss on ss.id=r.source_system_id
                left join application_users assigned on assigned.id=r.assigned_signer_id
                left join lateral (select normalized_value from natural_person_identifiers pi
                    where pi.natural_person_id=assigned.natural_person_id and pi.active=true
                    order by pi.verified desc, pi.created_at limit 1) identifier on true
                where r.id=:reportId
                """).param("reportId", reportId).query(this::mapSnapshot).optional();
    }

    Optional<WorkflowSignerOptionResponse> findActiveSigner(UUID signerId) {
        return jdbcClient.sql("""
                select u.id, u.username, trim(u.first_name || ' ' || u.last_name) display_name,
                        identifier.normalized_value signer_fiscal_code
                from application_users u
                join application_user_roles aur on aur.user_id=u.id
                join roles role on role.id=aur.role_id and role.code='SIGNER'
                join lateral (select normalized_value from natural_person_identifiers pi
                    where pi.natural_person_id=u.natural_person_id and pi.active=true
                    order by pi.verified desc, pi.created_at limit 1) identifier on true
                where u.id=:signerId and u.active=true
                """).param("signerId", signerId).query(this::mapSigner).optional();
    }

    List<WorkflowSignerOptionResponse> listActiveSigners() {
        return jdbcClient.sql("""
                select id, username, display_name, signer_fiscal_code from (
                    select distinct on (u.natural_person_id) u.id, u.username,
                           trim(u.first_name || ' ' || u.last_name) display_name,
                           identifier.normalized_value signer_fiscal_code, u.natural_person_id
                    from application_users u
                    join application_user_roles aur on aur.user_id=u.id
                    join roles role on role.id=aur.role_id and role.code='SIGNER'
                    join natural_person_identifiers identifier
                      on identifier.natural_person_id=u.natural_person_id and identifier.active=true
                    where u.active=true
                    order by u.natural_person_id, identifier.verified desc, u.created_at
                ) signers order by display_name, username
                """).query(this::mapSigner).list();
    }

    Optional<StoredWorkflowEvent> findEvent(UUID reportId, String operationKey) {
        return jdbcClient.sql("""
                select * from report_workflow_events
                where report_id=:reportId and operation_key=:operationKey
                """).param("reportId", reportId).param("operationKey", operationKey)
                .query(this::mapStoredEvent).optional();
    }

    List<ReportWorkflowEventResponse> history(UUID reportId) {
        return jdbcClient.sql("""
                select * from report_workflow_events where report_id=:reportId
                order by created_at desc, id desc limit 100
                """).param("reportId", reportId).query((rs, row) -> mapStoredEvent(rs, row).response()).list();
    }

    boolean applyChange(UUID reportId, long expectedVersion, ReportState state, UUID signerId,
                        boolean registerFirstPreview, boolean markMockSigned) {
        jdbcClient.sql("select set_config('signflow.workflow_transition_allowed', 'true', true)")
                .query(String.class).single();
        return jdbcClient.sql("""
                update reports
                set state=:state,
                    assigned_signer_id=:signerId,
                    first_previewed_at=case when :registerFirstPreview
                        then coalesce(first_previewed_at, now()) else first_previewed_at end,
                    signed_at=case when :markMockSigned then now() else signed_at end,
                    signature_kind=case when :markMockSigned then 'MOCK' else signature_kind end,
                    signature_artifact_notice=case when :markMockSigned
                        then 'MOCK ONLY - attestazione di collaudo, non è una firma digitale valida'
                        else signature_artifact_notice end,
                    workflow_version=workflow_version + 1,
                    modified_at=now(), updated_at=now()
                where id=:reportId and workflow_version=:expectedVersion
                """).param("state", state.name()).param("signerId", signerId)
                .param("registerFirstPreview", registerFirstPreview).param("reportId", reportId)
                .param("markMockSigned", markMockSigned)
                .param("expectedVersion", expectedVersion).update() == 1;
    }

    ReportWorkflowEventResponse insertEvent(UUID id, UUID reportId, String operationKey,
                                            ReportWorkflowOperation operationType, String fingerprint,
                                            ReportState fromState, ReportState toState,
                                            UUID previousSignerId, UUID newSignerId, String actorUsername,
                                            String reason, List<String> missingFields, long previousVersion,
                                            long resultingVersion, boolean firstPreview) {
        jdbcClient.sql("""
                insert into report_workflow_events
                    (id, report_id, operation_key, operation_type, request_fingerprint, from_state, to_state,
                     previous_signer_id, new_signer_id, actor_username, reason, missing_fields,
                     previous_version, resulting_version, first_preview)
                values (:id, :reportId, :operationKey, :operationType, :fingerprint, :fromState, :toState,
                        :previousSignerId, :newSignerId, :actorUsername, :reason, :missingFields,
                        :previousVersion, :resultingVersion, :firstPreview)
                """).param("id", id).param("reportId", reportId).param("operationKey", operationKey)
                .param("operationType", operationType.name()).param("fingerprint", fingerprint)
                .param("fromState", fromState.name()).param("toState", toState.name())
                .param("previousSignerId", previousSignerId).param("newSignerId", newSignerId)
                .param("actorUsername", actorUsername).param("reason", reason)
                .param("missingFields", String.join("|", missingFields)).param("previousVersion", previousVersion)
                .param("resultingVersion", resultingVersion).param("firstPreview", firstPreview).update();
        return findEvent(reportId, operationKey).orElseThrow().response();
    }

    private ReportWorkflowSnapshot mapSnapshot(ResultSet rs, int row) throws SQLException {
        return new ReportWorkflowSnapshot(
                rs.getObject("id", UUID.class), ReportState.valueOf(rs.getString("state")),
                rs.getLong("workflow_version"), rs.getObject("assigned_signer_id", UUID.class),
                rs.getString("signer_username"), rs.getBoolean("signer_active"),
                rs.getBoolean("signer_role"), rs.getString("signer_fiscal_code"),
                rs.getString("practice_identifier"), rs.getString("patient_identifier"),
                rs.getString("patient_first_name"), rs.getString("patient_last_name"),
                rs.getString("patient_fiscal_code"), rs.getString("document_type"),
                rs.getString("department"), rs.getObject("produced_at", OffsetDateTime.class),
                rs.getBoolean("source_system_active"), rs.getInt("active_document_count"),
                rs.getObject("first_previewed_at", OffsetDateTime.class));
    }

    private WorkflowSignerOptionResponse mapSigner(ResultSet rs, int row) throws SQLException {
        return new WorkflowSignerOptionResponse(rs.getObject("id", UUID.class), rs.getString("username"),
                rs.getString("display_name"), rs.getString("signer_fiscal_code"));
    }

    private StoredWorkflowEvent mapStoredEvent(ResultSet rs, int row) throws SQLException {
        ReportWorkflowEventResponse response = new ReportWorkflowEventResponse(
                rs.getObject("id", UUID.class), rs.getObject("report_id", UUID.class), rs.getString("operation_key"),
                ReportWorkflowOperation.valueOf(rs.getString("operation_type")),
                ReportState.valueOf(rs.getString("from_state")), ReportState.valueOf(rs.getString("to_state")),
                rs.getObject("previous_signer_id", UUID.class), rs.getObject("new_signer_id", UUID.class),
                rs.getString("actor_username"), rs.getString("reason"), split(rs.getString("missing_fields")),
                rs.getLong("previous_version"), rs.getLong("resulting_version"),
                rs.getBoolean("first_preview"), rs.getObject("created_at", OffsetDateTime.class));
        return new StoredWorkflowEvent(response, rs.getString("request_fingerprint"));
    }

    private List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split("\\|"));
    }

    record StoredWorkflowEvent(ReportWorkflowEventResponse response, String fingerprint) {
    }
}
