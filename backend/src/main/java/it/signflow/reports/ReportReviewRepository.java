package it.signflow.reports;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ReportReviewRepository {
    private final JdbcClient jdbc;

    ReportReviewRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<ReportReviewContext> context(UUID reportId) {
        return jdbc.sql("""
                select r.id, r.state, r.workflow_version, r.assigned_signer_id,
                        signer.natural_person_id signer_person_id, signer.username signer_username,
                        r.assigned_approver_id, approver.natural_person_id approver_person_id,
                        approver.username approver_username, coalesce(approver.active,false) approver_active,
                       exists(select 1 from application_user_roles ur join roles role on role.id=ur.role_id
                              where ur.user_id=approver.id and role.code='APPROVER') approver_role,
                       r.produced_by, r.review_separation_required, r.counter_signature_required,
                        r.counter_signer_id, counter_signer.natural_person_id counter_signer_person_id,
                        counter_signer.username counter_signer_username,
                       r.counter_signature_prepared_at
                from reports r
                left join application_users signer on signer.id=r.assigned_signer_id
                left join application_users approver on approver.id=r.assigned_approver_id
                left join application_users counter_signer on counter_signer.id=r.counter_signer_id
                where r.id=:reportId
                """).param("reportId", reportId).query(this::mapContext).optional();
    }

    List<WorkflowApproverOptionResponse> activeApprovers() {
        return jdbc.sql("""
                select distinct u.id, u.username, trim(u.first_name || ' ' || u.last_name) display_name
                from application_users u
                join application_user_roles ur on ur.user_id=u.id
                join roles role on role.id=ur.role_id and role.code='APPROVER'
                where u.active=true order by display_name, u.username
                """).query((rs, row) -> new WorkflowApproverOptionResponse(
                rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("display_name"))).list();
    }

    Optional<WorkflowApproverOptionResponse> activeApprover(UUID id) {
        return jdbc.sql("""
                select u.id, u.username, trim(u.first_name || ' ' || u.last_name) display_name
                from application_users u join application_user_roles ur on ur.user_id=u.id
                join roles role on role.id=ur.role_id and role.code='APPROVER'
                where u.id=:id and u.active=true
                """).param("id", id).query((rs, row) -> new WorkflowApproverOptionResponse(
                rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("display_name"))).optional();
    }

    boolean activeReviewParticipant(UUID id) {
        return jdbc.sql("""
                select count(*) from application_users u
                where u.id=:id and u.active=true and exists(
                    select 1 from application_user_roles ur join roles role on role.id=ur.role_id
                    where ur.user_id=u.id and role.code in ('APPROVER','SIGNER'))
                """).param("id", id).query(Integer.class).single() > 0;
    }

    boolean actorHasRole(String username, String role) {
        return jdbc.sql("""
                select count(*) from application_users u
                join application_user_roles ur on ur.user_id=u.id
                join roles r on r.id=ur.role_id
                where u.username=:username and u.active=true and r.code=:role
                """).param("username", username).param("role", role).query(Integer.class).single() > 0;
    }

    boolean sameNaturalPerson(String firstUsername, String secondUsername) {
        if (firstUsername == null || secondUsername == null) return false;
        return jdbc.sql("""
                select count(*) from application_users first_user
                join application_users second_user
                  on second_user.natural_person_id=first_user.natural_person_id
                where first_user.username=:first and second_user.username=:second
                """).param("first", firstUsername).param("second", secondUsername)
                .query(Integer.class).single() > 0;
    }

    boolean actorIsNaturalPerson(String username, UUID personId) {
        if (username == null || personId == null) return false;
        return jdbc.sql("""
                select count(*) from application_users
                where username=:username and natural_person_id=:personId and active=true
                """).param("username", username).param("personId", personId)
                .query(Integer.class).single() > 0;
    }

    Optional<UUID> naturalPersonForUser(UUID userId) {
        return jdbc.sql("select natural_person_id from application_users where id=:id and active=true")
                .param("id", userId).query(UUID.class).optional();
    }

    List<String> uploaders(UUID reportId) {
        return jdbc.sql("""
                select distinct uploaded_by from clinical_documents
                where report_id=:reportId and status='ACTIVE' order by uploaded_by
                """).param("reportId", reportId).query(String.class).list();
    }

    List<ApproverQueueItemResponse> queue(String username) {
        return jdbc.sql("""
                select r.id, r.internal_identifier, pm.last_name || ' ' || pm.first_name patient_name,
                       r.document_type, r.department, r.produced_at, r.state, r.workflow_version
                from reports r join patient_metadata pm on pm.id=r.patient_metadata_id
                join application_users a on a.id=r.assigned_approver_id
                join application_users me on me.username=:username and me.active=true
                where a.natural_person_id=me.natural_person_id and a.active=true
                  and r.state in ('REVIEW_PENDING','APPROVED')
                order by case when r.state='REVIEW_PENDING' then 0 else 1 end, r.produced_at desc
                """).param("username", username).query((rs, row) -> new ApproverQueueItemResponse(
                rs.getObject("id", UUID.class), rs.getString("internal_identifier"), rs.getString("patient_name"),
                rs.getString("document_type"), rs.getString("department"),
                rs.getObject("produced_at", OffsetDateTime.class), ReportState.valueOf(rs.getString("state")),
                rs.getLong("workflow_version"))).list();
    }

    boolean configure(UUID reportId, long expectedVersion, UUID approverId, boolean separation,
                      boolean counterRequired, UUID counterSignerId) {
        allowWorkflowUpdate();
        return jdbc.sql("""
                update reports set assigned_approver_id=:approverId,
                    review_separation_required=:separation,
                    counter_signature_required=:counterRequired,
                    counter_signer_id=:counterSignerId,
                    counter_signature_prepared_at=case
                        when counter_signer_id is distinct from :counterSignerId then null
                        else counter_signature_prepared_at end,
                    workflow_version=workflow_version+1, modified_at=now(), updated_at=now()
                where id=:reportId and workflow_version=:expectedVersion
                """).param("approverId", approverId).param("separation", separation)
                .param("counterRequired", counterRequired).param("counterSignerId", counterSignerId)
                .param("reportId", reportId).param("expectedVersion", expectedVersion).update() == 1;
    }

    void markCounterSignaturePrepared(UUID reportId, long version, UUID counterSignerId) {
        allowWorkflowUpdate();
        int changed = jdbc.sql("""
                update reports set counter_signer_id=:counterSignerId,
                    counter_signature_required=true, counter_signature_prepared_at=now(),
                    modified_at=now(), updated_at=now()
                where id=:reportId and workflow_version=:version
                """).param("counterSignerId", counterSignerId).param("reportId", reportId)
                .param("version", version).update();
        if (changed != 1) throw new IllegalStateException("Unable to prepare counter-signature");
    }

    Optional<StoredReviewDecision> decision(UUID reportId, String operationKey) {
        return jdbc.sql("""
                select * from report_review_decisions
                where report_id=:reportId and operation_key=:operationKey
                """).param("reportId", reportId).param("operationKey", operationKey)
                .query(this::mapStoredDecision).optional();
    }

    List<ReviewDecisionResponse> timeline(UUID reportId) {
        return jdbc.sql("""
                select * from report_review_decisions where report_id=:reportId
                order by created_at desc, id desc limit 100
                """).param("reportId", reportId).query((rs, row) -> mapStoredDecision(rs, row).response()).list();
    }

    ReviewDecisionResponse insertDecision(UUID reportId, String operationKey, ReviewDecisionType type,
                                          String fingerprint, String actor, String role, String reason,
                                          ReportState from, ReportState to, long previousVersion,
                                          long resultingVersion) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into report_review_decisions
                    (id, report_id, operation_key, decision_type, request_fingerprint, actor_username,
                     actor_role, reason, from_state, to_state, previous_version, resulting_version)
                values (:id,:reportId,:operationKey,:type,:fingerprint,:actor,:role,:reason,
                        :fromState,:toState,:previousVersion,:resultingVersion)
                """).param("id", id).param("reportId", reportId).param("operationKey", operationKey)
                .param("type", type.name()).param("fingerprint", fingerprint).param("actor", actor)
                .param("role", role).param("reason", reason).param("fromState", from.name())
                .param("toState", to.name()).param("previousVersion", previousVersion)
                .param("resultingVersion", resultingVersion).update();
        return decision(reportId, operationKey).orElseThrow().response();
    }

    private void allowWorkflowUpdate() {
        jdbc.sql("select set_config('signflow.workflow_transition_allowed','true',true)")
                .query(String.class).single();
    }

    private ReportReviewContext mapContext(ResultSet rs, int row) throws SQLException {
        return new ReportReviewContext(rs.getObject("id", UUID.class), ReportState.valueOf(rs.getString("state")),
                rs.getLong("workflow_version"), rs.getObject("assigned_signer_id", UUID.class),
                rs.getObject("signer_person_id", UUID.class), rs.getString("signer_username"),
                rs.getObject("assigned_approver_id", UUID.class), rs.getObject("approver_person_id", UUID.class),
                rs.getString("approver_username"), rs.getBoolean("approver_active"), rs.getBoolean("approver_role"),
                rs.getString("produced_by"), rs.getBoolean("review_separation_required"),
                rs.getBoolean("counter_signature_required"), rs.getObject("counter_signer_id", UUID.class),
                rs.getObject("counter_signer_person_id", UUID.class), rs.getString("counter_signer_username"),
                rs.getObject("counter_signature_prepared_at", OffsetDateTime.class));
    }

    private StoredReviewDecision mapStoredDecision(ResultSet rs, int row) throws SQLException {
        ReviewDecisionResponse response = new ReviewDecisionResponse(
                rs.getObject("id", UUID.class), rs.getObject("report_id", UUID.class),
                rs.getString("operation_key"), ReviewDecisionType.valueOf(rs.getString("decision_type")),
                rs.getString("actor_username"), rs.getString("actor_role"), rs.getString("reason"),
                ReportState.valueOf(rs.getString("from_state")), ReportState.valueOf(rs.getString("to_state")),
                rs.getLong("previous_version"), rs.getLong("resulting_version"),
                rs.getObject("created_at", OffsetDateTime.class));
        return new StoredReviewDecision(response, rs.getString("request_fingerprint"));
    }

    record StoredReviewDecision(ReviewDecisionResponse response, String fingerprint) {
    }
}
