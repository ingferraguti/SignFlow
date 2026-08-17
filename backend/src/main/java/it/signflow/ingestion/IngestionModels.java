package it.signflow.ingestion;

import it.signflow.reports.ReportState;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

enum Hl7MessageStatus { RECEIVED, PROCESSED, DISCARDED }
enum IngestionTransport { REST, MLLP }

record ParsedHl7Message(
        String sourceSystemCode,
        String messageType,
        String triggerEvent,
        String version,
        String controlId,
        String patientIdentifier,
        String patientFirstName,
        String patientLastName,
        String patientFiscalCode,
        LocalDate patientBirthDate,
        String episodeIdentifier,
        String externalReportIdentifier,
        String documentType,
        String department,
        OffsetDateTime producedAt,
        String signerIdentifier,
        byte[] document,
        String documentFilename) {
    @Override public byte[] document() { return document == null ? null : document.clone(); }
}

record SourceSystemPipeline(
        UUID id,
        String code,
        boolean active,
        boolean pdfA3Conversion,
        boolean visibleSignature,
        boolean multipleSignature,
        boolean sendUnsigned,
        boolean createCda,
        boolean passthrough) {
}

record IngestionResult(
        UUID messageId,
        Hl7MessageStatus status,
        UUID reportId,
        UUID documentId,
        ReportState reportState,
        String correlationId,
        String errorCode,
        String errorMessage,
        boolean idempotent) {
}

record Hl7MessageSummary(
        UUID id,
        OffsetDateTime receivedAt,
        OffsetDateTime processedAt,
        String transport,
        String status,
        String sourceSystemCode,
        String messageType,
        String triggerEvent,
        String controlId,
        String correlationId,
        UUID reportId,
        UUID documentId,
        String errorCode,
        String errorMessage) {
}

record Hl7MessagePage(List<Hl7MessageSummary> items, int page, int size, long total) {
}

record Hl7MessageDetail(
        Hl7MessageSummary message,
        String hl7Version,
        String payloadSha256,
        long rawSizeBytes,
        OffsetDateTime rawRetentionUntil,
        boolean cdaBuilderCalled,
        boolean documentNormalizerCalled,
        boolean pdfA3ConverterCalled,
        boolean passthroughApplied,
        List<String> pipelineSteps,
        UUID duplicateOfId,
        String maskedRawPreview) {
}

record MissingSignerReport(
        UUID reportId,
        String internalIdentifier,
        String sourceSystemCode,
        String documentType,
        String department,
        OffsetDateTime producedAt) {
}

record MissingSignerPage(List<MissingSignerReport> items, int page, int size, long total) {
}
