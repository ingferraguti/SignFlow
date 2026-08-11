package it.signflow.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record AuditEventResponse(
        UUID id,
        OffsetDateTime occurredAt,
        String eventType,
        String actorType,
        String actorId,
        String correlationId,
        String entityType,
        String entityId,
        String outcome,
        Map<String, Object> metadata,
        String reason,
        OffsetDateTime retentionUntil) {
}

record AuditPageResponse(List<AuditEventResponse> items, int page, int size, long total) {
}

record AuditRetentionResponse(int retentionDays, String updatedBy, OffsetDateTime updatedAt) {
}

record AuditRetentionRequest(int retentionDays) {
}

record AuditRetentionResult(int deletedEvents, OffsetDateTime appliedAt) {
}

record AuditSearchCriteria(
        String eventType,
        String actorId,
        String correlationId,
        String entityType,
        String entityId,
        String outcome,
        OffsetDateTime occurredFrom,
        OffsetDateTime occurredTo,
        int page,
        int size) {
}

record AuditRecordCommand(
        String eventType,
        String actorType,
        String actorId,
        String correlationId,
        String entityType,
        String entityId,
        String outcome,
        Map<String, ?> metadata,
        String reason) {
}
