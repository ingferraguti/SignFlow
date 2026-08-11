package it.signflow.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditController {
    private final AuditService service;

    public AdminAuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping("/events")
    AuditPageResponse search(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime occurredTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(criteria(eventType, actorId, correlationId, entityType, entityId, outcome, occurredFrom, occurredTo, page, size));
    }

    @GetMapping("/events/export")
    ResponseEntity<byte[]> export(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime occurredTo) {
        byte[] csv = service.exportCsv(criteria(eventType, actorId, correlationId, entityType, entityId, outcome, occurredFrom, occurredTo, 0, 100));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("signflow-audit.csv").build().toString())
                .body(csv);
    }

    @GetMapping("/reports/{reportId}/timeline")
    List<AuditEventResponse> reportTimeline(@PathVariable UUID reportId) {
        return service.reportTimeline(reportId);
    }

    @GetMapping("/documents/{documentId}/history")
    List<AuditEventResponse> documentHistory(@PathVariable UUID documentId) {
        return service.documentHistory(documentId);
    }

    @GetMapping("/reports/{reportId}/signatures")
    List<AuditEventResponse> signatureHistory(@PathVariable UUID reportId) {
        return service.signatureHistory(reportId);
    }

    @GetMapping("/retention")
    AuditRetentionResponse retention() {
        return service.retention();
    }

    @PutMapping("/retention")
    AuditRetentionResponse updateRetention(@RequestBody AuditRetentionRequest request, Authentication authentication) {
        return service.updateRetention(request.retentionDays(), authentication.getName());
    }

    @PostMapping("/retention/apply")
    AuditRetentionResult applyRetention(Authentication authentication) {
        return service.applyRetention(authentication.getName());
    }

    private AuditSearchCriteria criteria(String eventType, String actorId, String correlationId,
            String entityType, String entityId, String outcome, OffsetDateTime from, OffsetDateTime to,
            int page, int size) {
        return new AuditSearchCriteria(eventType, actorId, correlationId, entityType, entityId, outcome, from, to, page, size);
    }
}
