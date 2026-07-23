package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportDetailResponse(
        UUID id,
        String internalIdentifier,
        String externalIdentifier,
        String fseIdentifier,
        PracticeResponse practice,
        PatientMetadataResponse patient,
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
        boolean pdfA3Conversion,
        boolean visibleSignature,
        boolean multipleSignature,
        boolean sendUnsigned,
        boolean createCda,
        boolean passthrough,
        ReportState state) {
}
