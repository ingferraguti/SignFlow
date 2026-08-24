package it.signflow.ingestion;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/adapters/signature-requests")
public class ServiceSignatureRequestAdapterController {
    private final ServiceSignatureRequestService service;

    ServiceSignatureRequestAdapterController(ServiceSignatureRequestService service) { this.service = service; }

    @GetMapping(value = "/{requestId}/signable-document", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> signableDocument(@PathVariable UUID requestId) {
        var content = service.signableDocument(requestId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(content.bytes().length)
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(content.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Document-SHA256", content.sha256()).cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }

    @PostMapping(value = "/{requestId}/signed-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ServiceSignatureRequestResponse> signedDocument(@PathVariable UUID requestId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        var response = service.registerSignedDocument(requestId, file, idempotencyKey, actor(jwt));
        return ResponseEntity.ok().header("X-Correlation-ID", response.correlationId())
                .cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping(value = "/{requestId}/conservation-status", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ServiceSignatureRequestResponse> conservationStatus(@PathVariable UUID requestId,
            @Valid @RequestBody ConservationStatusInput input,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        var response = service.registerConservation(requestId, input, idempotencyKey, actor(jwt));
        return ResponseEntity.ok().header("X-Correlation-ID", response.correlationId())
                .cacheControl(CacheControl.noStore()).body(response);
    }

    private String actor(Jwt jwt) {
        if (jwt == null) return "system.adapter";
        String username = jwt.getClaimAsString("preferred_username");
        return username == null || username.isBlank() ? jwt.getSubject() : username;
    }
}
