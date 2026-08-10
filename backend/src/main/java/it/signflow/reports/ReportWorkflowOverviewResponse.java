package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ReportWorkflowOverviewResponse(
        UUID reportId,
        ReportState state,
        long version,
        UUID assignedSignerId,
        String signerUsername,
        OffsetDateTime firstPreviewedAt,
        List<String> missingFields,
        Set<ReportState> allowedTargets,
        boolean signerAssignmentAllowed,
        boolean readinessEvaluationAllowed,
        boolean administrativeCorrectionAllowed,
        List<ReportWorkflowEventResponse> history) {
}
