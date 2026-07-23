package it.signflow.reports;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/reports/{reportId}/documents")
public class AdminClinicalDocumentController {
    private final ClinicalDocumentService service;

    public AdminClinicalDocumentController(ClinicalDocumentService service) {
        this.service = service;
    }

    @GetMapping
    List<ClinicalDocumentResponse> list(@PathVariable UUID reportId,
                                        @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return service.list(reportId, includeDeleted);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ClinicalDocumentResponse> upload(@PathVariable UUID reportId,
                                                     @RequestParam("file") MultipartFile file,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(reportId, file, username(jwt)));
    }

    @GetMapping("/{documentId}/content")
    ResponseEntity<ByteArrayResource> content(@PathVariable UUID reportId, @PathVariable UUID documentId,
                                               @RequestParam(defaultValue = "inline") String disposition) {
        if (!("inline".equalsIgnoreCase(disposition) || "attachment".equalsIgnoreCase(disposition))) {
            throw new IllegalArgumentException("Disposition must be inline or attachment");
        }
        ClinicalDocumentService.DocumentContent content = service.content(reportId, documentId);
        String safeDisposition = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        ContentDisposition header = ContentDisposition.builder(safeDisposition)
                .filename(content.metadata().originalFilename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(content.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, header.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(content.bytes()));
    }

    @GetMapping("/{documentId}/temporary-url")
    TemporaryDocumentUrlResponse temporaryUrl(@PathVariable UUID reportId, @PathVariable UUID documentId,
                                               @RequestParam(defaultValue = "inline") String disposition) {
        return service.temporaryUrl(reportId, documentId, disposition);
    }

    @DeleteMapping("/{documentId}")
    ResponseEntity<Void> delete(@PathVariable UUID reportId, @PathVariable UUID documentId,
                                @AuthenticationPrincipal Jwt jwt) {
        service.delete(reportId, documentId, username(jwt));
        return ResponseEntity.noContent().build();
    }

    private String username(Jwt jwt) {
        String username = jwt == null ? null : jwt.getClaimAsString("preferred_username");
        return username == null ? "unknown" : username;
    }
}
