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
public class AdminReportWorkflowController {
    private final ReportWorkflowService service;

    public AdminReportWorkflowController(ReportWorkflowService service) {
        this.service = service;
    }

    @GetMapping("/workflow/signers")
    List<WorkflowSignerOptionResponse> signers() {
        return service.activeSigners();
    }

    @GetMapping("/{reportId}/workflow")
    ReportWorkflowOverviewResponse overview(@PathVariable UUID reportId) {
        return service.overview(reportId);
    }

    @PostMapping("/{reportId}/workflow/assign-signer")
    ReportWorkflowOperationResponse assignSigner(@PathVariable UUID reportId,
                                                 @Valid @RequestBody AssignSignerRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return service.assignSigner(reportId, request, username(jwt));
    }

    @PostMapping("/{reportId}/workflow/evaluate-readiness")
    ReportWorkflowOperationResponse evaluateReadiness(@PathVariable UUID reportId,
                                                       @Valid @RequestBody EvaluateReadinessRequest request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return service.evaluateReadiness(reportId, request, username(jwt));
    }

    @PostMapping("/{reportId}/workflow/admin-correction")
    ReportWorkflowOperationResponse administrativeCorrection(@PathVariable UUID reportId,
                                                              @Valid @RequestBody AdminCorrectionRequest request,
                                                              @AuthenticationPrincipal Jwt jwt) {
        return service.administrativeCorrection(reportId, request, username(jwt));
    }

    private String username(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("preferred_username");
    }
}
