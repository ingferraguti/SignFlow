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
@RequestMapping("/api/admin/reports")
public class AdminReportReviewController {
    private final ReportReviewService service;

    public AdminReportReviewController(ReportReviewService service) {
        this.service = service;
    }

    @GetMapping("/review/approvers")
    List<WorkflowApproverOptionResponse> approvers() {
        return service.activeApprovers();
    }

    @GetMapping("/{reportId}/review")
    ReportReviewOverviewResponse overview(@PathVariable UUID reportId) {
        return service.overview(reportId);
    }

    @PostMapping("/{reportId}/review/configure")
    ReviewOperationResponse configure(@PathVariable UUID reportId, @Valid @RequestBody ConfigureReviewRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return service.configure(reportId, request, username(jwt));
    }

    @PostMapping("/{reportId}/review/return")
    ReviewOperationResponse returnToPrevious(@PathVariable UUID reportId,
                                             @Valid @RequestBody ReviewReasonRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        return service.returnToPrevious(reportId, request, username(jwt));
    }

    @PostMapping("/{reportId}/review/prepare-counter-signature")
    ReviewOperationResponse prepareCounterSignature(@PathVariable UUID reportId,
                                                     @Valid @RequestBody PrepareCounterSignatureRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return service.prepareCounterSignature(reportId, request, username(jwt));
    }

    private String username(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("preferred_username");
    }
}
