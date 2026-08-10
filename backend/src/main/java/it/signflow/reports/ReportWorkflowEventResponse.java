package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReportWorkflowEventResponse(
        UUID id,
        UUID reportId,
        String operationKey,
        ReportWorkflowOperation operationType,
        ReportState fromState,
        ReportState toState,
        UUID previousSignerId,
        UUID newSignerId,
        String actorUsername,
        String reason,
        List<String> missingFields,
        long previousVersion,
        long resultingVersion,
        boolean firstPreview,
        OffsetDateTime createdAt) {
}
