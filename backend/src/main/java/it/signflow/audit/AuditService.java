package it.signflow.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditService {
    private static final Set<String> FORBIDDEN_METADATA = Set.of(
            "document", "content", "payload", "password", "token", "authorization", "otp",
            "fiscalcode", "taxcode", "patient", "diagnosis", "healthdata", "clinicaldata");
    private final AuditRepository repository;
    private final ObjectMapper objectMapper;

    AuditService(AuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditRecordCommand command) {
        validateRequired(command);
        Map<String, Object> safe = sanitize(command.metadata());
        try {
            repository.append(command, objectMapper.writeValueAsString(safe));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit metadata cannot be serialized", exception);
        }
    }

    public AuditPageResponse search(AuditSearchCriteria criteria) {
        int page = Math.max(0, criteria.page());
        int size = Math.min(100, Math.max(1, criteria.size()));
        return repository.search(new AuditSearchCriteria(criteria.eventType(), criteria.actorId(), criteria.correlationId(),
                criteria.entityType(), criteria.entityId(), criteria.outcome(), criteria.occurredFrom(), criteria.occurredTo(), page, size));
    }

    public List<AuditEventResponse> reportTimeline(UUID reportId) {
        return repository.reportTimeline(reportId);
    }

    public List<AuditEventResponse> documentHistory(UUID documentId) {
        return repository.timeline("DOCUMENT", documentId.toString());
    }

    public List<AuditEventResponse> signatureHistory(UUID reportId) {
        return repository.reportTimeline(reportId).stream().filter(event -> event.eventType().startsWith("SIGNATURE_")).toList();
    }

    public byte[] exportCsv(AuditSearchCriteria criteria) {
        AuditSearchCriteria export = new AuditSearchCriteria(criteria.eventType(), criteria.actorId(), criteria.correlationId(),
                criteria.entityType(), criteria.entityId(), criteria.outcome(), criteria.occurredFrom(), criteria.occurredTo(), 0, 100);
        StringBuilder csv = new StringBuilder("timestamp,event_type,actor_type,actor_id,correlation_id,entity_type,entity_id,outcome,reason\n");
        for (AuditEventResponse event : search(export).items()) {
            csv.append(csv(event.occurredAt())).append(',').append(csv(event.eventType())).append(',')
                    .append(csv(event.actorType())).append(',').append(csv(event.actorId())).append(',')
                    .append(csv(event.correlationId())).append(',').append(csv(event.entityType())).append(',')
                    .append(csv(event.entityId())).append(',').append(csv(event.outcome())).append(',')
                    .append(csv(event.reason())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public AuditRetentionResponse retention() {
        return repository.retention();
    }

    @Transactional
    public AuditRetentionResponse updateRetention(int days, String actor) {
        if (days < 30 || days > 3650) throw new IllegalArgumentException("Retention must be between 30 and 3650 days");
        repository.updateRetention(days, actor);
        record(new AuditRecordCommand("AUDIT_RETENTION_CHANGED", "USER", actor, UUID.randomUUID().toString(),
                "CONFIGURATION", "audit-retention", "SUCCESS", Map.of("retentionDays", days), null));
        return repository.retention();
    }

    @Transactional
    public AuditRetentionResult applyRetention(String actor) {
        int deleted = repository.applyRetention();
        record(new AuditRecordCommand("AUDIT_RETENTION_APPLIED", "USER", actor, UUID.randomUUID().toString(),
                "CONFIGURATION", "audit-retention", "SUCCESS", Map.of("deletedEventCount", deleted), null));
        return new AuditRetentionResult(deleted, OffsetDateTime.now());
    }

    private Map<String, Object> sanitize(Map<String, ?> metadata) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (metadata == null) return safe;
        if (metadata.size() > 20) throw new IllegalArgumentException("Audit metadata exceeds 20 fields");
        metadata.forEach((key, value) -> {
            String normalized = key.replace("_", "").toLowerCase(Locale.ROOT);
            if (FORBIDDEN_METADATA.stream().anyMatch(normalized::contains)) {
                throw new IllegalArgumentException("Sensitive metadata key is forbidden: " + key);
            }
            if (!(value == null || value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof UUID)) {
                throw new IllegalArgumentException("Audit metadata values must be scalar");
            }
            String text = value instanceof String string ? string : null;
            safe.put(key, text != null && text.length() > 200 ? text.substring(0, 200) : value);
        });
        return safe;
    }

    private void validateRequired(AuditRecordCommand c) {
        if (List.of(c.eventType(), c.actorType(), c.actorId(), c.correlationId(), c.entityType(), c.entityId(), c.outcome())
                .stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Audit event required fields cannot be blank");
        }
    }

    private String csv(Object value) {
        String text = value == null ? "" : value.toString();
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
