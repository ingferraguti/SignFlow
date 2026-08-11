package it.signflow.signatures;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/signer/signatures")
public class SignerSignatureController {
    private final SignatureService service;

    public SignerSignatureController(SignatureService service) {
        this.service = service;
    }

    @PostMapping("/provider-sessions")
    ProviderSessionResponse session(@Valid @RequestBody ProviderSessionRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return service.openSession(username(jwt), request);
    }

    @GetMapping("/batches")
    List<SignatureBatchResponse> batches(@AuthenticationPrincipal Jwt jwt) {
        return service.list(username(jwt));
    }

    @PostMapping("/batches")
    SignatureBatchResponse create(@Valid @RequestBody CreateSignatureBatchRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.create(username(jwt), request);
    }

    @PostMapping("/single")
    SignatureBatchResponse single(@Valid @RequestBody SingleSignatureRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.signSingle(username(jwt), request);
    }

    @GetMapping("/batches/{batchId}")
    SignatureBatchResponse detail(@PathVariable UUID batchId, @AuthenticationPrincipal Jwt jwt) {
        return service.detail(username(jwt), batchId);
    }

    @PostMapping("/batches/{batchId}/confirm")
    SignatureBatchResponse confirm(@PathVariable UUID batchId,
                                   @Valid @RequestBody SignatureOperationRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.confirm(username(jwt), batchId, request);
    }

    @PostMapping("/batches/{batchId}/start")
    SignatureBatchResponse start(@PathVariable UUID batchId,
                                 @Valid @RequestBody SignatureOperationRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return service.start(username(jwt), batchId, request);
    }

    @PostMapping("/batches/{batchId}/cancel")
    SignatureBatchResponse cancel(@PathVariable UUID batchId,
                                  @Valid @RequestBody SignatureOperationRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(username(jwt), batchId, request);
    }

    @PostMapping("/batches/{batchId}/attempts/{attemptId}/retry")
    SignatureBatchResponse retry(@PathVariable UUID batchId, @PathVariable UUID attemptId,
                                 @Valid @RequestBody SignatureOperationRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return service.retry(username(jwt), batchId, attemptId, request);
    }

    @GetMapping("/artifacts/{artifactId}")
    ResponseEntity<ByteArrayResource> artifact(@PathVariable UUID artifactId,
                                               @AuthenticationPrincipal Jwt jwt) {
        SignatureArtifact artifact = service.artifact(username(jwt), artifactId);
        byte[] bytes = artifact.content().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(artifact.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-SignFlow-Signature-Type", "MOCK-NON-LEGAL")
                .body(new ByteArrayResource(bytes));
    }

    private String username(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("preferred_username");
    }
}
