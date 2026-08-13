package it.signflow.reports;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportReviewService {
    private final ReportReviewRepository repository;
    private final ReportWorkflowService workflow;

    public ReportReviewService(ReportReviewRepository repository, ReportWorkflowService workflow) {
        this.repository = repository;
        this.workflow = workflow;
    }

    public ReportReviewOverviewResponse overview(UUID reportId) {
        ReportReviewContext context = context(reportId);
        return overview(context);
    }

    public ReportReviewOverviewResponse overviewForApprover(UUID reportId, String actor) {
        ReportReviewContext context = assignedApprover(reportId, actor);
        return overview(context);
    }

    public List<WorkflowApproverOptionResponse> activeApprovers() {
        return repository.activeApprovers();
    }

    public List<ApproverQueueItemResponse> queue(String actor) {
        requireRole(actor, "APPROVER");
        return repository.queue(actor);
    }

    @Transactional
    public ReviewOperationResponse configure(UUID reportId, ConfigureReviewRequest request, String actor) {
        requireRole(actor, "ADMINISTRATOR");
        String key = text(request.operationKey(), "Operation key is required");
        String fingerprint = fingerprint(ReviewDecisionType.CONFIGURED, request.approverId(),
                request.counterSignerId(), request.separationRequired(), request.counterSignatureRequired(), null);
        ReviewOperationResponse repeated = repeated(reportId, key, fingerprint, ReviewDecisionType.CONFIGURED);
        if (repeated != null) return repeated;
        ReportReviewContext current = context(reportId);
        requireVersion(current, request.expectedVersion());
        WorkflowApproverOptionResponse approver = request.approverId() == null ? null
                : repository.activeApprover(request.approverId()).orElseThrow(() -> unprocessable(
                "The selected approver is not active or does not have the APPROVER role"));
        validateCounterSigner(current, request.counterSignerId());
        validateSeparation(current, approver == null ? null : approver.username(),
                request.approverId(), request.separationRequired());
        if (!repository.configure(reportId, current.version(), request.approverId(), request.separationRequired(),
                request.counterSignatureRequired(), request.counterSignerId())) {
            throw conflict("The report was updated by another operation; reload it and retry");
        }
        repository.insertDecision(reportId, key, ReviewDecisionType.CONFIGURED, fingerprint, actor,
                "ADMINISTRATOR", null, current.state(), current.state(), current.version(), current.version() + 1);
        return response(ReviewDecisionType.CONFIGURED, reportId, false);
    }

    @Transactional
    public ReviewOperationResponse requestReview(UUID reportId, ReviewActionRequest request, String actor) {
        requireRole(actor, "SIGNER");
        ReportReviewContext current = context(reportId);
        if (!repository.actorIsNaturalPerson(actor, current.signerNaturalPersonId())) {
            throw forbidden("Only the assigned natural person can request review");
        }
        ReviewOperationResponse replay = replayTransition(reportId, request.operationKey(),
                ReviewDecisionType.REQUESTED, null);
        if (replay != null) return replay;
        requireReviewReady(current);
        validateSeparation(current, current.approverUsername(), current.approverId(), current.separationRequired());
        return transition(current, request.expectedVersion(), request.operationKey(), ReviewDecisionType.REQUESTED,
                ReportWorkflowOperation.REQUEST_REVIEW, ReportState.REVIEW_PENDING, actor, "SIGNER", null);
    }

    @Transactional
    public ReviewOperationResponse recordView(UUID reportId, ReviewActionRequest request, String actor) {
        ReportReviewContext current = assignedApprover(reportId, actor);
        ReviewOperationResponse replay = replayTransition(reportId, request.operationKey(),
                ReviewDecisionType.VIEWED, null);
        if (replay != null) return replay;
        requireState(current, ReportState.REVIEW_PENDING);
        return transition(current, request.expectedVersion(), request.operationKey(), ReviewDecisionType.VIEWED,
                ReportWorkflowOperation.VIEW_REVIEW_DOCUMENT, ReportState.REVIEW_PENDING, actor, "APPROVER", null);
    }

    @Transactional
    public ReviewOperationResponse approve(UUID reportId, ReviewActionRequest request, String actor) {
        ReportReviewContext current = assignedApprover(reportId, actor);
        ReviewOperationResponse replay = replayTransition(reportId, request.operationKey(),
                ReviewDecisionType.APPROVED, null);
        if (replay != null) return replay;
        requireState(current, ReportState.REVIEW_PENDING);
        validateSeparation(current, actor, current.approverId(), current.separationRequired());
        return transition(current, request.expectedVersion(), request.operationKey(), ReviewDecisionType.APPROVED,
                ReportWorkflowOperation.APPROVE_REVIEW, ReportState.APPROVED, actor, "APPROVER", null);
    }

    @Transactional
    public ReviewOperationResponse reject(UUID reportId, ReviewReasonRequest request, String actor) {
        ReportReviewContext current = assignedApprover(reportId, actor);
        String reason = text(request.reason(), "Rejection reason is required");
        ReviewOperationResponse replay = replayTransition(reportId, request.operationKey(),
                ReviewDecisionType.REJECTED, reason);
        if (replay != null) return replay;
        requireState(current, ReportState.REVIEW_PENDING);
        return transition(current, request.expectedVersion(), request.operationKey(), ReviewDecisionType.REJECTED,
                ReportWorkflowOperation.REJECT_REVIEW, ReportState.PREVIEWED, actor, "APPROVER", reason);
    }

    @Transactional
    public ReviewOperationResponse returnToPrevious(UUID reportId, ReviewReasonRequest request, String actor) {
        requireRole(actor, "ADMINISTRATOR");
        String reason = text(request.reason(), "Return reason is required");
        ReviewOperationResponse replay = replayTransition(reportId, request.operationKey(),
                ReviewDecisionType.RETURNED, reason);
        if (replay != null) return replay;
        ReportReviewContext current = context(reportId);
        ReportState target = switch (current.state()) {
            case REVIEW_PENDING -> ReportState.PREVIEWED;
            case APPROVED -> ReportState.REVIEW_PENDING;
            default -> throw conflict("Return is not allowed from " + current.state());
        };
        return transition(current, request.expectedVersion(), request.operationKey(), ReviewDecisionType.RETURNED,
                ReportWorkflowOperation.RETURN_REVIEW, target, actor, "ADMINISTRATOR", reason);
    }

    @Transactional
    public ReviewOperationResponse prepareCounterSignature(UUID reportId, PrepareCounterSignatureRequest request,
                                                           String actor) {
        requireRole(actor, "ADMINISTRATOR");
        ReportReviewContext current = context(reportId);
        requireState(current, ReportState.APPROVED);
        UUID counterSignerId = request.counterSignerId() == null ? current.counterSignerId() : request.counterSignerId();
        validateCounterSigner(current, counterSignerId);
        if (counterSignerId == null) throw unprocessable("A counter-signer is required");
        String key = text(request.operationKey(), "Operation key is required");
        String requestFingerprint = fingerprint(ReviewDecisionType.COUNTER_SIGNATURE_PREPARED, null,
                counterSignerId, false, true, null);
        ReviewOperationResponse repeated = repeated(reportId, key, requestFingerprint,
                ReviewDecisionType.COUNTER_SIGNATURE_PREPARED);
        if (repeated != null) return repeated;
        ReportWorkflowOperationResponse changed = workflow.transitionForReview(reportId, request.expectedVersion(), key,
                ReportWorkflowOperation.PREPARE_COUNTER_SIGNATURE, ReportState.APPROVED, actor, null);
        repository.markCounterSignaturePrepared(reportId, changed.resultingVersion(), counterSignerId);
        repository.insertDecision(reportId, key, ReviewDecisionType.COUNTER_SIGNATURE_PREPARED,
                requestFingerprint, actor, "ADMINISTRATOR", null, changed.fromState(), changed.toState(),
                changed.previousVersion(), changed.resultingVersion());
        return response(ReviewDecisionType.COUNTER_SIGNATURE_PREPARED, reportId, changed.idempotent());
    }

    private ReviewOperationResponse transition(ReportReviewContext current, long expectedVersion, String operationKey,
                                               ReviewDecisionType type, ReportWorkflowOperation workflowOperation,
                                               ReportState target, String actor, String role, String reason) {
        String key = text(operationKey, "Operation key is required");
        String requestFingerprint = fingerprint(type, null, null, false, false, reason);
        ReviewOperationResponse repeated = repeated(current.reportId(), key, requestFingerprint, type);
        if (repeated != null) return repeated;
        ReportWorkflowOperationResponse changed = workflow.transitionForReview(current.reportId(), expectedVersion,
                key, workflowOperation, target, actor, reason);
        if (!changed.idempotent()) {
            repository.insertDecision(current.reportId(), key, type, requestFingerprint, actor, role, reason,
                    changed.fromState(), changed.toState(), changed.previousVersion(), changed.resultingVersion());
        }
        return response(type, current.reportId(), changed.idempotent());
    }

    private ReportReviewOverviewResponse overview(ReportReviewContext context) {
        List<String> uploaders = repository.uploaders(context.reportId());
        return new ReportReviewOverviewResponse(context.reportId(), context.state(), context.version(),
                context.signerId(), context.signerUsername(), context.approverId(), context.approverUsername(),
                context.producedBy(), uploaders, context.separationRequired(), context.counterSignatureRequired(),
                context.counterSignerId(), context.counterSignerUsername(), context.counterSignaturePreparedAt(),
                context.state() == ReportState.PREVIEWED && validApprover(context),
                context.state() == ReportState.REVIEW_PENDING && validApprover(context),
                context.state() == ReportState.REVIEW_PENDING || context.state() == ReportState.APPROVED,
                context.state() == ReportState.APPROVED && context.counterSignatureRequired()
                        && context.counterSignaturePreparedAt() == null,
                repository.timeline(context.reportId()));
    }

    private ReviewOperationResponse response(ReviewDecisionType type, UUID reportId, boolean idempotent) {
        ReportReviewOverviewResponse result = overview(reportId);
        return new ReviewOperationResponse(type, result.state(), result.version(), idempotent, result);
    }

    private ReviewOperationResponse repeated(UUID reportId, String key, String fingerprint, ReviewDecisionType type) {
        return repository.decision(reportId, key).map(stored -> {
            if (!stored.fingerprint().equals(fingerprint) || stored.response().decisionType() != type) {
                throw conflict("The idempotency key was already used for a different request");
            }
            return response(type, reportId, true);
        }).orElse(null);
    }

    private ReviewOperationResponse replayTransition(UUID reportId, String operationKey,
                                                     ReviewDecisionType type, String reason) {
        String key = text(operationKey, "Operation key is required");
        return repeated(reportId, key, fingerprint(type, null, null, false, false, reason), type);
    }

    private ReportReviewContext assignedApprover(UUID reportId, String actor) {
        requireRole(actor, "APPROVER");
        ReportReviewContext current = context(reportId);
        if (!repository.actorIsNaturalPerson(actor, current.approverNaturalPersonId())) {
            throw forbidden("Review is assigned to another natural person");
        }
        return current;
    }

    private void requireReviewReady(ReportReviewContext context) {
        requireState(context, ReportState.PREVIEWED);
        if (!validApprover(context)) throw unprocessable("An active approver with the APPROVER role is required");
    }

    private boolean validApprover(ReportReviewContext context) {
        return context.approverId() != null && context.approverActive() && context.approverRole();
    }

    private void validateSeparation(ReportReviewContext context, String approverUsername, UUID approverId,
                                    boolean required) {
        if (!required || approverId == null) return;
        UUID approverPersonId = repository.naturalPersonForUser(approverId).orElse(null);
        if (Objects.equals(approverPersonId, context.signerNaturalPersonId())) {
            throw unprocessable("Role separation requires approver and signer to be different natural persons");
        }
        if (repository.sameNaturalPerson(approverUsername, context.producedBy())
                || repository.uploaders(context.reportId()).stream()
                    .anyMatch(uploader -> repository.sameNaturalPerson(approverUsername, uploader))) {
            throw unprocessable("The document producer or uploader cannot approve when role separation is required");
        }
    }

    private void validateCounterSigner(ReportReviewContext context, UUID counterSignerId) {
        if (counterSignerId == null) return;
        UUID counterPersonId = repository.naturalPersonForUser(counterSignerId).orElse(null);
        if (Objects.equals(counterPersonId, context.signerNaturalPersonId())) {
            throw unprocessable("The counter-signer must be different from the primary signer");
        }
        if (!repository.activeReviewParticipant(counterSignerId)) {
            throw unprocessable("The counter-signer must be an active signer or approver");
        }
    }

    private ReportReviewContext context(UUID reportId) {
        return repository.context(reportId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Report not found"));
    }

    private void requireRole(String actor, String role) {
        if (actor == null || actor.isBlank() || !repository.actorHasRole(actor, role)) {
            throw forbidden(role + " profile not available");
        }
    }

    private void requireState(ReportReviewContext current, ReportState expected) {
        if (current.state() != expected) throw conflict("Operation requires " + expected + " but report is " + current.state());
    }

    private void requireVersion(ReportReviewContext current, long expected) {
        if (current.version() != expected) throw conflict("The report was updated by another operation; expected version "
                + expected + " but current version is " + current.version());
    }

    private String fingerprint(ReviewDecisionType type, UUID approverId, UUID counterSignerId,
                               boolean separation, boolean counterRequired, String reason) {
        String value = type + "|" + Objects.toString(approverId, "") + "|"
                + Objects.toString(counterSignerId, "") + "|" + separation + "|" + counterRequired + "|"
                + Objects.toString(reason, "");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String text(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException unprocessable(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
