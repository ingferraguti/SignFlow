package it.signflow.ingestion;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceSignatureRequestResponse(
        UUID requestId,
        String externalRequestId,
        String sourceSystemCode,
        ServiceDocumentType documentType,
        String documentSubtype,
        String documentSubtypeDisplayName,
        String status,
        Signer signer,
        SourceDocument sourceDocument,
        NormalizedDocument normalizedDocument,
        Signature signature,
        Conservation conservation,
        String correlationId,
        OffsetDateTime submittedAt,
        boolean idempotent) {

    public record Signer(UUID naturalPersonId, String displayName) {}
    public record SourceDocument(String originalFilename, String contentType, long sizeBytes, String sha256) {}
    public record NormalizedDocument(UUID documentId, String filename, String contentType, long sizeBytes,
                                     String sha256, String profile, String pdfaPart,
                                     String pdfaConformance, String validator, int pageCount) {}
    public record Signature(boolean signed, OffsetDateTime signedAt, Integer signatureCount,
                            String validation, boolean signedDocumentAvailable, String signedDocumentSha256,
                            String downloadPath) {}
    public record Conservation(String status, boolean sent, OffsetDateTime sentAt, OffsetDateTime completedAt,
                               String remoteReference, String errorCode) {}
}
