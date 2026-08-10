package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReportWorkflowOperationResponse(
        UUID reportId,
        ReportWorkflowOperation operationType,
        ReportState fromState,
        ReportState toState,
        long previousVersion,
        long resultingVersion,
        UUID assignedSignerId,
        OffsetDateTime firstPreviewedAt,
        List<String> missingFields,
        boolean idempotent) {
}
