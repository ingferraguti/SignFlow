package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportSummaryResponse(
        UUID id,
        String internalIdentifier,
        String externalIdentifier,
        String fseIdentifier,
        String practiceIdentifier,
        String patientIdentifier,
        String patientDisplayName,
        UUID assignedSignerId,
        String signerUsername,
        String signerFiscalCode,
        UUID sourceSystemId,
        String sourceSystemCode,
        String documentType,
        String department,
        OffsetDateTime producedAt,
        OffsetDateTime modifiedAt,
        OffsetDateTime signedAt,
        ReportState state,
        boolean signatureEligible) {
}
