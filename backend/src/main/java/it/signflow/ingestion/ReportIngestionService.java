package it.signflow.ingestion;

import it.signflow.reports.MinioObjectStorage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportIngestionService {
    private final Hl7V2Parser parser;
    private final IngestionRepository repository;
    private final ReportIngestionPipeline pipeline;
    private final MinioObjectStorage storage;
    private final IngestionProperties properties;

    ReportIngestionService(Hl7V2Parser parser, IngestionRepository repository,
                           ReportIngestionPipeline pipeline, MinioObjectStorage storage,
                           IngestionProperties properties) {
        this.parser = parser;
        this.repository = repository;
        this.pipeline = pipeline;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional
    public IngestionResult ingestHl7(String raw, String expectedSourceSystem, String operationKey,
                                     String correlationId, IngestionTransport transport) {
        byte[] bytes = raw == null ? new byte[0] : raw.getBytes(StandardCharsets.UTF_8);
        boolean oversized = bytes.length > properties.maxMessageBytes();
        String payloadHash = sha256(bytes);
        ParsedHl7Message parsed = null;
        Hl7ParsingException parsingFailure = null;
        try {
            if (oversized) throw new Hl7ParsingException("MESSAGE_TOO_LARGE", "HL7 message exceeds the configured limit");
            parsed = parser.parse(raw);
            if (text(expectedSourceSystem) != null
                    && !expectedSourceSystem.trim().equalsIgnoreCase(parsed.sourceSystemCode())) {
                parsingFailure = new Hl7ParsingException("SOURCE_SYSTEM_MISMATCH",
                        "SourceSystem header does not match MSH-3");
            }
        } catch (Hl7ParsingException exception) {
            parsingFailure = exception;
        }
        SourceSystemPipeline source = parsed == null ? null
                : repository.sourceSystem(parsed.sourceSystemCode()).orElse(null);
        String effectiveOperationKey = idempotencyKey(operationKey,
                parsed == null ? null : parsed.controlId(), payloadHash);
        String deduplicationKey = sha256((source == null ? "UNKNOWN" : source.id().toString())
                + "|" + effectiveOperationKey);
        String safeCorrelation = correlation(correlationId);
        UUID messageId = UUID.randomUUID();
        OffsetDateTime retention = OffsetDateTime.now().plusDays(properties.rawRetentionDays());
        String actor = "system.ingestion." + transport.name().toLowerCase();

        boolean reserved = repository.reserve(messageId, source, parsed, effectiveOperationKey,
                deduplicationKey, payloadHash, safeCorrelation, bytes.length, retention, transport, actor);
        if (!reserved) {
            IngestionRepository.StoredMessage existing = repository.byDeduplicationKey(deduplicationKey).orElseThrow();
            if (existing.payloadHash().equals(payloadHash)) return existing.result(true);
            UUID conflictId = UUID.randomUUID();
            repository.insertConflictingDuplicate(conflictId, source, parsed, effectiveOperationKey, payloadHash,
                    safeCorrelation, bytes.length, retention, transport, actor, existing.summary().id());
            storeRaw(conflictId, bytes);
            return repository.stored(conflictId).orElseThrow().result(false);
        }

        try {
            if (!oversized && bytes.length > 0) storeRaw(messageId, bytes);
        } catch (RuntimeException exception) {
            repository.discarded(messageId, "RAW_STORAGE_FAILED", "Raw HL7 storage failed", null,
                    List.of("RAW_STORAGE_FAILED"));
            return repository.stored(messageId).orElseThrow().result(false);
        }
        if (parsingFailure != null) {
            repository.discarded(messageId, parsingFailure.code(), parsingFailure.getMessage(), null,
                    List.of("HL7_REJECTED"));
            return repository.stored(messageId).orElseThrow().result(false);
        }
        if (source == null) {
            repository.discarded(messageId, "SOURCE_SYSTEM_NOT_FOUND", "SourceSystem is not configured", null,
                    List.of("SOURCE_SYSTEM_REJECTED"));
            return repository.stored(messageId).orElseThrow().result(false);
        }
        if (!source.active()) {
            repository.discarded(messageId, "SOURCE_SYSTEM_INACTIVE", "SourceSystem is inactive", null,
                    List.of("SOURCE_SYSTEM_REJECTED"));
            return repository.stored(messageId).orElseThrow().result(false);
        }

        try {
            ReportIngestionPipeline.PipelineResult result = pipeline.process(messageId, parsed, source, safeCorrelation);
            repository.processed(messageId, result.reportId(), result.documentId(), result.cdaCalled(),
                    result.normalizerCalled(), result.converterCalled(), result.passthrough(), result.steps());
        } catch (PipelineDiscardException exception) {
            repository.discarded(messageId, exception.code(), exception.getMessage(), exception.reportId(),
                    List.of("PIPELINE_DISCARDED"));
        } catch (IllegalArgumentException exception) {
            repository.discarded(messageId, "PIPELINE_VALIDATION_FAILED", "Document pipeline validation failed", null,
                    List.of("PIPELINE_VALIDATION_FAILED"));
        } catch (RuntimeException exception) {
            repository.discarded(messageId, "PIPELINE_FAILED", "Document pipeline failed", null,
                    List.of("PIPELINE_FAILED"));
        }
        return repository.stored(messageId).orElseThrow().result(false);
    }

    private void storeRaw(UUID messageId, byte[] bytes) {
        String objectKey = "ingestion/hl7/" + OffsetDateTime.now().getYear() + "/" + messageId + ".hl7";
        storage.put(objectKey, new ByteArrayInputStream(bytes), bytes.length, "application/hl7-v2");
        repository.rawStored(messageId, objectKey);
    }

    private String idempotencyKey(String supplied, String controlId, String payloadHash) {
        String value = text(controlId) != null ? controlId.trim()
                : text(supplied) != null ? supplied.trim() : "sha256:" + payloadHash.substring(0, 32);
        if (value.length() > 160) throw new IllegalArgumentException("Idempotency key exceeds 160 characters");
        return value;
    }

    private String correlation(String value) {
        String result = text(value) == null ? UUID.randomUUID().toString() : value.trim();
        if (!result.matches("[A-Za-z0-9._:-]{1,160}")) throw new IllegalArgumentException("Invalid correlation ID");
        return result;
    }

    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private String text(String value) { return value == null || value.isBlank() ? null : value; }
}
