package it.signflow.ingestion;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {
    private final ReportIngestionService service;

    public IngestionController(ReportIngestionService service) { this.service = service; }

    @PostMapping(value = "/hl7", consumes = {"application/hl7-v2", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<IngestionResult> ingestHl7(
            @RequestBody String raw,
            @RequestHeader(value = "X-Source-System", required = false) String sourceSystem,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String operationKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        IngestionResult result = service.ingestHl7(raw, sourceSystem, operationKey, correlationId,
                IngestionTransport.REST);
        HttpStatus status = result.status() == Hl7MessageStatus.DISCARDED
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : result.idempotent() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).header("X-Correlation-ID", result.correlationId())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(result);
    }
}
