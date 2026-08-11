package it.signflow.reports;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approver")
public class ApproverReportController {
    private final ReportReviewService reviewService;
    private final ReportService reportService;
    private final ClinicalDocumentService documentService;

    public ApproverReportController(ReportReviewService reviewService, ReportService reportService,
                                    ClinicalDocumentService documentService) {
        this.reviewService = reviewService;
        this.reportService = reportService;
        this.documentService = documentService;
    }

    @GetMapping("/reports")
    List<ApproverQueueItemResponse> queue(@AuthenticationPrincipal Jwt jwt) {
        return reviewService.queue(username(jwt));
    }

    @GetMapping("/reports/{reportId}")
    ReportDetailResponse detail(@PathVariable UUID reportId, @AuthenticationPrincipal Jwt jwt) {
        reviewService.overviewForApprover(reportId, username(jwt));
        return reportService.get(reportId);
    }

    @GetMapping("/reports/{reportId}/review")
    ReportReviewOverviewResponse review(@PathVariable UUID reportId, @AuthenticationPrincipal Jwt jwt) {
        return reviewService.overviewForApprover(reportId, username(jwt));
    }

    @GetMapping("/reports/{reportId}/documents")
    List<ClinicalDocumentResponse> documents(@PathVariable UUID reportId, @AuthenticationPrincipal Jwt jwt) {
        reviewService.overviewForApprover(reportId, username(jwt));
        return documentService.list(reportId, false);
    }

    @PostMapping("/reports/{reportId}/documents/{documentId}/preview")
    ReviewDocumentPreviewResponse preview(@PathVariable UUID reportId, @PathVariable UUID documentId,
                                          @Valid @RequestBody ReviewActionRequest request,
                                          @AuthenticationPrincipal Jwt jwt) {
        String actor = username(jwt);
        reviewService.overviewForApprover(reportId, actor);
        TemporaryDocumentUrlResponse temporary = documentService.temporaryUrl(reportId, documentId, "inline");
        ReviewOperationResponse viewed = reviewService.recordView(reportId, request, actor);
        return new ReviewDocumentPreviewResponse(temporary.url(), temporary.expiresAt(), viewed.state(),
                viewed.version(), viewed.review());
    }

    @PostMapping("/reports/{reportId}/approve")
    ReviewOperationResponse approve(@PathVariable UUID reportId, @Valid @RequestBody ReviewActionRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return reviewService.approve(reportId, request, username(jwt));
    }

    @PostMapping("/reports/{reportId}/reject")
    ReviewOperationResponse reject(@PathVariable UUID reportId, @Valid @RequestBody ReviewReasonRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return reviewService.reject(reportId, request, username(jwt));
    }

    private String username(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("preferred_username");
    }
}
