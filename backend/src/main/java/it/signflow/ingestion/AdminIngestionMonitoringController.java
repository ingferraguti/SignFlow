package it.signflow.ingestion;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/monitoring")
public class AdminIngestionMonitoringController {
    private final IngestionMonitoringService service;
    public AdminIngestionMonitoringController(IngestionMonitoringService service) { this.service = service; }

    @GetMapping("/messages")
    Hl7MessagePage messages(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceSystemCode,
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(status, sourceSystemCode, messageType, correlationId, receivedFrom, receivedTo, page, size);
    }

    @GetMapping("/messages/{id}") Hl7MessageDetail message(@PathVariable UUID id) { return service.detail(id); }

    @GetMapping("/reports-without-signer")
    MissingSignerPage reportsWithoutSigner(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.missingSigners(page, size);
    }
}
