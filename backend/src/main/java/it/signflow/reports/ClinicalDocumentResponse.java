package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClinicalDocumentResponse(
        UUID id,
        UUID reportId,
        String sha256,
        String mimeType,
        long sizeBytes,
        int version,
        String originalFilename,
        String objectIdentifier,
        String uploadedBy,
        OffsetDateTime uploadedAt,
        ClinicalDocumentStatus status,
        OffsetDateTime deletedAt,
        String deletedBy) {
}
