package it.signflow.reports;

import it.signflow.identity.PageResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/signer")
public class SignerReportController {
    private final SignerReportService service;
    private final ReportReviewService reviewService;

    public SignerReportController(SignerReportService service, ReportReviewService reviewService) {
        this.service = service;
        this.reviewService = reviewService;
    }

    @GetMapping("/home")
    SignerHomeResponse home(@AuthenticationPrincipal Jwt jwt) {
        return service.home(username(jwt));
    }

    @GetMapping("/reports")
    PageResponse<ReportSummaryResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String patient,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate producedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate producedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return service.search(username(jwt), new SignerReportSearchRequest(query, patient, documentType, department,
                state, producedFrom, producedTo, signedFrom, signedTo, page, size));
    }

    @GetMapping("/reports/{reportId}")
    ReportDetailResponse detail(@PathVariable UUID reportId, @AuthenticationPrincipal Jwt jwt) {
        return service.detail(username(jwt), reportId);
    }

    @GetMapping("/reports/{reportId}/documents")
    List<ClinicalDocumentResponse> documents(@PathVariable UUID reportId, @AuthenticationPrincipal Jwt jwt) {
        return service.documents(username(jwt), reportId);
    }

    @PostMapping("/reports/{reportId}/documents/{documentId}/preview")
    SignerPreviewResponse preview(@PathVariable UUID reportId, @PathVariable UUID documentId,
                                  @Valid @RequestBody PreviewRegistrationRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.preview(username(jwt), reportId, documentId, request);
    }

    @GetMapping("/reports/{reportId}/review")
    ReportReviewOverviewResponse review(@PathVariable UUID reportId, @AuthenticationPrincipal Jwt jwt) {
        service.detail(username(jwt), reportId);
        return reviewService.overview(reportId);
    }

    @PostMapping("/reports/{reportId}/review/request")
    ReviewOperationResponse requestReview(@PathVariable UUID reportId,
                                          @Valid @RequestBody ReviewActionRequest request,
                                          @AuthenticationPrincipal Jwt jwt) {
        service.detail(username(jwt), reportId);
        return reviewService.requestReview(reportId, request, username(jwt));
    }

    @GetMapping("/reports/{reportId}/documents/{documentId}/content")
    ResponseEntity<ByteArrayResource> download(@PathVariable UUID reportId, @PathVariable UUID documentId,
                                               @AuthenticationPrincipal Jwt jwt) {
        ClinicalDocumentService.DocumentContent content = service.download(username(jwt), reportId, documentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.metadata().originalFilename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(content.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff").body(new ByteArrayResource(content.bytes()));
    }

    @GetMapping("/states")
    List<ReportStateLegendResponse> states() {
        return service.legend();
    }

    @GetMapping("/profile")
    SignerProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return service.profile(username(jwt));
    }

    private String username(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("preferred_username");
    }
}
