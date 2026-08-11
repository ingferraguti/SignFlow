package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

record ReportReviewContext(
        UUID reportId, ReportState state, long version,
        UUID signerId, String signerUsername,
        UUID approverId, String approverUsername, boolean approverActive, boolean approverRole,
        String producedBy, boolean separationRequired, boolean counterSignatureRequired,
        UUID counterSignerId, String counterSignerUsername, OffsetDateTime counterSignaturePreparedAt) {
}
