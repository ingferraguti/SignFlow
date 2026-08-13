package it.signflow.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AuditRepository {
    private static final String FILTER = """
            where (:eventType = '' or event_type = :eventType)
              and (:actorId = '' or lower(actor_id) like '%' || lower(:actorId) || '%')
              and (:correlationId = '' or correlation_id = :correlationId)
              and (:entityType = '' or entity_type = :entityType)
              and (:entityId = '' or entity_id = :entityId)
              and (:outcome = '' or outcome = :outcome)
              and (cast(:occurredFrom as timestamptz) is null or occurred_at >= cast(:occurredFrom as timestamptz))
              and (cast(:occurredTo as timestamptz) is null or occurred_at <= cast(:occurredTo as timestamptz))
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    AuditRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    void append(AuditRecordCommand command, String metadataJson) {
        jdbc.sql("select append_audit_event(:eventType, :actorType, :actorId, :correlationId, :entityType, :entityId, :outcome, cast(:metadata as jsonb), :reason, now())")
                .param("eventType", command.eventType()).param("actorType", command.actorType())
                .param("actorId", command.actorId()).param("correlationId", command.correlationId())
                .param("entityType", command.entityType()).param("entityId", command.entityId())
                .param("outcome", command.outcome()).param("metadata", metadataJson)
                .param("reason", command.reason()).query((rs, row) -> 0).single();
    }

    AuditPageResponse search(AuditSearchCriteria criteria) {
        long total = bind(jdbc.sql("select count(*) from audit_events " + FILTER), criteria)
                .query(Long.class).single();
        List<AuditEventResponse> items = bind(jdbc.sql("select * from audit_events " + FILTER
                + " order by occurred_at desc, id desc limit :limit offset :offset"), criteria)
                .param("limit", criteria.size()).param("offset", criteria.page() * criteria.size())
                .query(this::map).list();
        return new AuditPageResponse(items, criteria.page(), criteria.size(), total);
    }

    List<AuditEventResponse> timeline(String entityType, String entityId) {
        return jdbc.sql("select * from audit_events where entity_type=:type and entity_id=:id order by occurred_at, id")
                .param("type", entityType).param("id", entityId).query(this::map).list();
    }

    List<AuditEventResponse> reportTimeline(UUID reportId) {
        return jdbc.sql("""
                select distinct a.* from audit_events a
                where (a.entity_type='REPORT' and a.entity_id=:reportId)
                   or (a.entity_type='DOCUMENT' and (
                       a.metadata->>'reportId'=:reportId
                       or exists (select 1 from audit_events linked
                           where linked.entity_type='DOCUMENT'
                             and linked.entity_id=a.entity_id
                             and linked.metadata->>'reportId'=:reportId)))
                   or (a.entity_type='SIGNATURE_BATCH' and exists (
                       select 1 from audit_events linked
                       where linked.entity_type='SIGNATURE_ATTEMPT'
                         and linked.metadata->>'batchId'=a.entity_id
                         and linked.metadata->>'reportId'=:reportId))
                   or (a.entity_type='SIGNATURE_ATTEMPT' and a.metadata->>'reportId'=:reportId)
                order by a.occurred_at, a.id
                """).param("reportId", reportId.toString()).query(this::map).list();
    }

    AuditRetentionResponse retention() {
        return jdbc.sql("select retention_days, updated_by, updated_at from audit_retention_policy where id=1")
                .query((rs, row) -> new AuditRetentionResponse(rs.getInt(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class))).single();
    }

    void updateRetention(int days, String actor) {
        jdbc.sql("update audit_retention_policy set retention_days=:days, updated_by=:actor, updated_at=now() where id=1")
                .param("days", days).param("actor", actor).update();
    }

    int applyRetention() {
        jdbc.sql("select set_config('signflow.audit_retention_allowed', 'true', true)").query(String.class).single();
        return jdbc.sql("delete from audit_events where retention_until < now()").update();
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, AuditSearchCriteria c) {
        return statement.param("eventType", normalized(c.eventType())).param("actorId", normalized(c.actorId()))
                .param("correlationId", normalized(c.correlationId())).param("entityType", normalized(c.entityType()))
                .param("entityId", normalized(c.entityId())).param("outcome", normalized(c.outcome()))
                .param("occurredFrom", c.occurredFrom()).param("occurredTo", c.occurredTo());
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private AuditEventResponse map(ResultSet rs, int row) throws SQLException {
        try {
            Map<String, Object> metadata = objectMapper.readValue(rs.getString("metadata"), new TypeReference<>() {});
            return new AuditEventResponse(rs.getObject("id", UUID.class), rs.getObject("occurred_at", OffsetDateTime.class),
                    rs.getString("event_type"), rs.getString("actor_type"), rs.getString("actor_id"),
                    rs.getString("correlation_id"), rs.getString("entity_type"), rs.getString("entity_id"),
                    rs.getString("outcome"), metadata, rs.getString("reason"),
                    rs.getObject("retention_until", OffsetDateTime.class));
        } catch (Exception exception) {
            throw new SQLException("Invalid audit metadata", exception);
        }
    }
}
