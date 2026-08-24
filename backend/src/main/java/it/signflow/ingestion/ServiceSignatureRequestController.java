package it.signflow.ingestion;

import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/integration/signature-requests")
public class ServiceSignatureRequestController {
    private final ServiceSignatureRequestService service;

    ServiceSignatureRequestController(ServiceSignatureRequestService service) {
        this.service = service;
    }

    @GetMapping("/document-types")
    List<ServiceDocumentTypeCatalogResponse> documentTypes() {
        return service.catalog();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ServiceSignatureRequestResponse> submit(
            @Valid @RequestPart("request") ServiceSignatureRequestInput request,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "X-Source-System", required = false) String sourceSystem,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt) {
        ServiceSignatureRequestResponse response = service.submit(request, file, sourceSystem, idempotencyKey,
                correlationId, actor(jwt));
        HttpStatus status = response.idempotent() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .location(URI.create("/api/integration/signature-requests/" + response.requestId()))
                .header("X-Correlation-ID", response.correlationId())
                .cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/{requestId}")
    ResponseEntity<ServiceSignatureRequestResponse> find(
            @PathVariable UUID requestId,
            @RequestHeader(value = "X-Source-System", required = false) String sourceSystem) {
        ServiceSignatureRequestResponse response = service.find(requestId, sourceSystem);
        return ResponseEntity.ok().header("X-Correlation-ID", response.correlationId())
                .cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping(value = "/{requestId}/signed-document", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> signedDocument(@PathVariable UUID requestId,
            @RequestHeader(value = "X-Source-System", required = false) String sourceSystem) {
        var content = service.signedDocument(requestId, sourceSystem);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .contentLength(content.bytes().length)
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(content.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Document-SHA256", content.sha256()).cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }

    private String actor(Jwt jwt) {
        if (jwt == null) return "system.integration";
        String username = jwt.getClaimAsString("preferred_username");
        return username == null || username.isBlank() ? jwt.getSubject() : username;
    }
}
