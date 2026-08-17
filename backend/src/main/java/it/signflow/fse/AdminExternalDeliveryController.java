package it.signflow.fse;

import static it.signflow.fse.ExternalDeliveryModels.*;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/external-deliveries")
public class AdminExternalDeliveryController {
    private final ExternalDeliveryWorkflowService service;
    public AdminExternalDeliveryController(ExternalDeliveryWorkflowService service) { this.service = service; }

    @GetMapping
    OperationPage search(@RequestParam(required = false) String channel,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String correlationId,
                         @RequestParam(required = false) String reportIdentifier,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return service.search(channel, state, correlationId, reportIdentifier, page, size);
    }

    @GetMapping("/{id}") OperationDetail detail(@PathVariable UUID id) { return service.detail(id); }

    @PostMapping("/fse/reports/{reportId}")
    OperationDetail sendFse(@PathVariable UUID reportId, @Valid @RequestBody CommandRequest request, Principal principal) {
        return service.sendFse(reportId, request, principal.getName());
    }

    @PostMapping("/conservation/reports/{reportId}")
    OperationDetail sendConservation(@PathVariable UUID reportId, @Valid @RequestBody CommandRequest request,
                                     Principal principal) {
        return service.sendConservation(reportId, request, principal.getName());
    }

    @PostMapping("/{id}/retry")
    OperationDetail retry(@PathVariable UUID id, @Valid @RequestBody CommandRequest request, Principal principal) {
        return service.retry(id, request, principal.getName());
    }

    @PostMapping("/{id}/reconcile")
    OperationDetail reconcile(@PathVariable UUID id, @Valid @RequestBody CommandRequest request, Principal principal) {
        return service.reconcile(id, request, principal.getName());
    }

    @GetMapping("/receipts/{id}/download")
    ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
        ExternalDeliveryWorkflowService.ReceiptContent receipt = service.receipt(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(receipt.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(receipt.mimeType())).body(receipt.bytes());
    }
}
