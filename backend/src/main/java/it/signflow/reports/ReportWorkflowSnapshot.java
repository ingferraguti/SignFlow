package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

record ReportWorkflowSnapshot(
        UUID reportId,
        ReportState state,
        long version,
        UUID assignedSignerId,
        String signerUsername,
        boolean signerActive,
        boolean signerRole,
        String signerFiscalCode,
        String practiceIdentifier,
        String patientIdentifier,
        String patientFirstName,
        String patientLastName,
        String patientFiscalCode,
        String documentType,
        String department,
        OffsetDateTime producedAt,
        boolean sourceSystemActive,
        int activeDocumentCount,
        OffsetDateTime firstPreviewedAt) {
}
