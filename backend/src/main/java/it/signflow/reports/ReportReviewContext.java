package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

record ReportReviewContext(
        UUID reportId, ReportState state, long version,
        UUID signerId, UUID signerNaturalPersonId, String signerUsername,
        UUID approverId, UUID approverNaturalPersonId, String approverUsername,
        boolean approverActive, boolean approverRole,
        String producedBy, boolean separationRequired, boolean counterSignatureRequired,
        UUID counterSignerId, UUID counterSignerNaturalPersonId, String counterSignerUsername,
        OffsetDateTime counterSignaturePreparedAt) {
}
