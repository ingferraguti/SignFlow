package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReportReviewOverviewResponse(
        UUID reportId, ReportState state, long version,
        UUID assignedSignerId, String signerUsername,
        UUID assignedApproverId, String approverUsername,
        String producedBy, List<String> uploadedBy,
        boolean separationRequired, boolean counterSignatureRequired,
        UUID counterSignerId, String counterSignerUsername,
        OffsetDateTime counterSignaturePreparedAt,
        boolean requestAllowed, boolean reviewDecisionAllowed,
        boolean returnAllowed, boolean counterSignaturePreparationAllowed,
        List<ReviewDecisionResponse> timeline) {
}
