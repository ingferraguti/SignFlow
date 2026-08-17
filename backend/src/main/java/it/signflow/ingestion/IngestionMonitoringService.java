package it.signflow.ingestion;

import it.signflow.reports.MinioObjectStorage;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
final class IngestionMonitoringService {
    private final IngestionRepository repository;
    private final MinioObjectStorage storage;

    IngestionMonitoringService(IngestionRepository repository, MinioObjectStorage storage) {
        this.repository = repository; this.storage = storage;
    }

    Hl7MessagePage search(String status, String sourceSystemCode, String messageType, String correlationId,
                          OffsetDateTime from, OffsetDateTime to, int page, int size) {
        return repository.search(status, sourceSystemCode, messageType, correlationId, from, to, page, size);
    }

    Hl7MessageDetail detail(UUID id) {
        IngestionRepository.StoredMessage stored = repository.stored(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "HL7 message not found"));
        String preview = maskedPreview(stored);
        return new Hl7MessageDetail(stored.summary(), stored.version(), stored.payloadHash(), stored.rawSize(),
                stored.retentionUntil(), stored.cdaCalled(), stored.normalizerCalled(), stored.converterCalled(),
                stored.passthrough(), stored.steps(), stored.duplicateOfId(), preview);
    }

    MissingSignerPage missingSigners(int page, int size) { return repository.missingSigners(page, size); }

    private String maskedPreview(IngestionRepository.StoredMessage stored) {
        if (stored.rawObjectKey() == null) return "Raw non disponibile o non conservato per limiti di sicurezza.";
        byte[] raw = storage.get(stored.rawObjectKey());
        String text = new String(raw, StandardCharsets.UTF_8);
        return Arrays.stream(text.replace("\r\n", "\r").replace('\n', '\r').split("\r"))
                .filter(line -> !line.isBlank())
                .limit(100)
                .map(line -> line.startsWith("MSH|")
                        ? "MSH|***|SOURCE=" + safe(stored.summary().sourceSystemCode())
                            + "|TYPE=" + safe(stored.summary().messageType())
                            + "^" + safe(stored.summary().triggerEvent())
                            + "|CONTROL=***|VERSION=" + safe(stored.version())
                        : segmentName(line) + "|*** CONTENUTO MASCHERATO ***")
                .reduce((left, right) -> left + "\n" + right).orElse("Payload HL7 mascherato.");
    }

    private String segmentName(String line) {
        String name = line.length() >= 3 ? line.substring(0, 3) : "SEG";
        return name.matches("[A-Z0-9]{3}") ? name : "SEG";
    }
    private String safe(String value) { return value == null || value.isBlank() ? "—" : value.replaceAll("[^A-Za-z0-9_.-]", "-"); }
}
