package it.signflow.reports;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportWorkflowService {
    private final ReportWorkflowRepository repository;

    public ReportWorkflowService(ReportWorkflowRepository repository) {
        this.repository = repository;
    }

    public ReportWorkflowOverviewResponse overview(UUID reportId) {
        ReportWorkflowSnapshot snapshot = snapshot(reportId);
        return new ReportWorkflowOverviewResponse(
                reportId, snapshot.state(), snapshot.version(), snapshot.assignedSignerId(),
                snapshot.signerUsername(), snapshot.firstPreviewedAt(), missingFields(snapshot),
                ReportWorkflowRules.allowedTargets(snapshot.state()),
                ReportWorkflowRules.canAssignSigner(snapshot.state()),
                ReportWorkflowRules.canEvaluateReadiness(snapshot.state()),
                isAdminCorrectable(snapshot.state()), repository.history(reportId));
    }

    public List<WorkflowSignerOptionResponse> activeSigners() {
        return repository.listActiveSigners();
    }

    @Transactional
    public ReportWorkflowOperationResponse assignSigner(UUID reportId, AssignSignerRequest request, String actor) {
        String key = operationKey(request.operationKey());
        String fingerprint = fingerprint(ReportWorkflowOperation.ASSIGN_SIGNER, request.signerId(), null, null);
        ReportWorkflowOperationResponse repeated = repeated(reportId, key, fingerprint);
        if (repeated != null) return repeated;

        ReportWorkflowSnapshot current = snapshot(reportId);
        requireVersion(current, request.expectedVersion());
        if (!ReportWorkflowRules.canAssignSigner(current.state())) {
            throw conflict("Signer assignment is not allowed from " + current.state());
        }

        WorkflowSignerOptionResponse signer = request.signerId() == null ? null
                : repository.findActiveSigner(request.signerId())
                .orElseThrow(() -> unprocessable("The selected signer is not active or does not have the SIGNER role"));
        List<String> missing = missingFields(current, signer, request.signerId() == null);
        ReportState target = readinessState(missing);
        ensureTransition(current.state(), target);
        return apply(current, key, ReportWorkflowOperation.ASSIGN_SIGNER, fingerprint, target,
                request.signerId(), actor(actor), null, missing, false);
    }

    @Transactional
    public ReportWorkflowOperationResponse evaluateReadiness(UUID reportId, EvaluateReadinessRequest request,
                                                              String actor) {
        String key = operationKey(request.operationKey());
        String fingerprint = fingerprint(ReportWorkflowOperation.EVALUATE_READINESS, null, null, null);
        ReportWorkflowOperationResponse repeated = repeated(reportId, key, fingerprint);
        if (repeated != null) return repeated;

        ReportWorkflowSnapshot current = snapshot(reportId);
        requireVersion(current, request.expectedVersion());
        if (!ReportWorkflowRules.canEvaluateReadiness(current.state())) {
            throw conflict("Readiness evaluation is not allowed from " + current.state());
        }
        List<String> missing = missingFields(current);
        ReportState target = readinessState(missing);
        ensureTransition(current.state(), target);
        return apply(current, key, ReportWorkflowOperation.EVALUATE_READINESS, fingerprint, target,
                current.assignedSignerId(), actor(actor), null, missing, false);
    }

    @Transactional
    public ReportWorkflowOperationResponse registerFirstPreview(UUID reportId, PreviewRegistrationRequest request,
                                                                String actor) {
        String key = operationKey(request.operationKey());
        String fingerprint = fingerprint(ReportWorkflowOperation.FIRST_PREVIEW, null, null, null);
        ReportWorkflowOperationResponse repeated = repeated(reportId, key, fingerprint);
        if (repeated != null) return repeated;

        ReportWorkflowSnapshot current = snapshot(reportId);
        requireVersion(current, request.expectedVersion());
        if (!ReportWorkflowRules.canRegisterPreview(current.state())) {
            throw conflict("Preview is not allowed from " + current.state());
        }
        ReportState target = current.state() == ReportState.READY_TO_SIGN
                ? ReportState.PREVIEWED : current.state();
        ensureTransition(current.state(), target);
        boolean firstPreview = current.firstPreviewedAt() == null;
        return apply(current, key, ReportWorkflowOperation.FIRST_PREVIEW, fingerprint, target,
                current.assignedSignerId(), actor(actor), null, missingFields(current), firstPreview);
    }

    @Transactional
    public ReportWorkflowOperationResponse administrativeCorrection(UUID reportId,
                                                                    AdminCorrectionRequest request,
                                                                    String actor) {
        String key = operationKey(request.operationKey());
        String reason = requiredText(request.reason(), "Administrative correction reason is required");
        String fingerprint = fingerprint(ReportWorkflowOperation.ADMIN_CORRECTION, null,
                request.targetState(), reason);
        ReportWorkflowOperationResponse repeated = repeated(reportId, key, fingerprint);
        if (repeated != null) return repeated;

        ReportWorkflowSnapshot current = snapshot(reportId);
        requireVersion(current, request.expectedVersion());
        if (!ReportWorkflowRules.canAdminCorrect(current.state(), request.targetState())) {
            throw conflict("Administrative correction from " + current.state() + " to "
                    + request.targetState() + " is not allowed");
        }
        List<String> missing = missingFields(current);
        ReportState consistentTarget = readinessState(missing);
        if (request.targetState() != consistentTarget) {
            throw unprocessable("The requested state is inconsistent with current preconditions; expected "
                    + consistentTarget);
        }
        return apply(current, key, ReportWorkflowOperation.ADMIN_CORRECTION, fingerprint,
                request.targetState(), current.assignedSignerId(), actor(actor), reason, missing, false);
    }

    @Transactional
    ReportWorkflowOperationResponse transitionForReview(UUID reportId, long expectedVersion, String operationKey,
                                                        ReportWorkflowOperation operation, ReportState target,
                                                        String actor, String reason) {
        if (!Set.of(ReportWorkflowOperation.REQUEST_REVIEW, ReportWorkflowOperation.VIEW_REVIEW_DOCUMENT,
                ReportWorkflowOperation.APPROVE_REVIEW, ReportWorkflowOperation.REJECT_REVIEW,
                ReportWorkflowOperation.RETURN_REVIEW, ReportWorkflowOperation.PREPARE_COUNTER_SIGNATURE)
                .contains(operation)) {
            throw new IllegalArgumentException("Unsupported review workflow operation");
        }
        String key = operationKey(operationKey);
        String normalizedReason = blank(reason) ? null : reason.trim();
        String requestFingerprint = fingerprint(operation, null, target, normalizedReason);
        ReportWorkflowOperationResponse repeated = repeated(reportId, key, requestFingerprint);
        if (repeated != null) return repeated;
        ReportWorkflowSnapshot current = snapshot(reportId);
        requireVersion(current, expectedVersion);
        ensureTransition(current.state(), target);
        return apply(current, key, operation, requestFingerprint, target, current.assignedSignerId(),
                actor(actor), normalizedReason, missingFields(current), false);
    }

    ReportWorkflowSnapshot currentSnapshot(UUID reportId) {
        return snapshot(reportId);
    }

    private ReportWorkflowOperationResponse apply(ReportWorkflowSnapshot current, String operationKey,
                                                   ReportWorkflowOperation operation, String fingerprint,
                                                   ReportState target, UUID newSignerId, String actor, String reason,
                                                   List<String> missingFields, boolean firstPreview) {
        boolean changeApplied = repository.applyChange(current.reportId(), current.version(), target, newSignerId, firstPreview);
        if (!changeApplied) {
            ReportWorkflowOperationResponse repeated = repeated(current.reportId(), operationKey, fingerprint);
            if (repeated != null) return repeated;
            throw conflict("The report was updated by another operation; reload it and retry");
        }
        long resultingVersion = current.version() + 1;
        ReportWorkflowEventResponse event = repository.insertEvent(UUID.randomUUID(), current.reportId(), operationKey,
                operation, fingerprint, current.state(), target, current.assignedSignerId(), newSignerId,
                actor, reason, missingFields, current.version(), resultingVersion, firstPreview);
        ReportWorkflowSnapshot updatedSnapshot = snapshot(current.reportId());
        return response(event, updatedSnapshot.firstPreviewedAt(), false);
    }

    private ReportWorkflowOperationResponse repeated(UUID reportId, String operationKey, String fingerprint) {
        return repository.findEvent(reportId, operationKey).map(stored -> {
            if (!stored.fingerprint().equals(fingerprint)) {
                throw conflict("The idempotency key was already used for a different request");
            }
            ReportWorkflowSnapshot current = snapshot(reportId);
            return response(stored.response(), current.firstPreviewedAt(), true);
        }).orElse(null);
    }

    private ReportWorkflowOperationResponse response(ReportWorkflowEventResponse event,
                                                     java.time.OffsetDateTime firstPreviewedAt,
                                                     boolean idempotent) {
        return new ReportWorkflowOperationResponse(event.reportId(),
                event.operationType(), event.fromState(), event.toState(), event.previousVersion(),
                event.resultingVersion(), event.newSignerId(), firstPreviewedAt,
                event.missingFields(), idempotent);
    }

    private ReportWorkflowSnapshot snapshot(UUID reportId) {
        return repository.findSnapshot(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    private List<String> missingFields(ReportWorkflowSnapshot snapshot) {
        return missingFields(snapshot, null, false);
    }

    private List<String> missingFields(ReportWorkflowSnapshot snapshot, WorkflowSignerOptionResponse override,
                                       boolean clearSigner) {
        List<String> missing = new ArrayList<>();
        if (clearSigner || (override == null && snapshot.assignedSignerId() == null)) {
            missing.add("SIGNER");
        } else if (override != null) {
            addIfBlank(missing, "SIGNER_FISCAL_CODE", override.signerFiscalCode());
        } else {
            if (!snapshot.signerActive()) missing.add("SIGNER_INACTIVE");
            if (!snapshot.signerRole()) missing.add("SIGNER_ROLE");
            addIfBlank(missing, "SIGNER_FISCAL_CODE", snapshot.signerFiscalCode());
        }
        addIfBlank(missing, "PRACTICE", snapshot.practiceIdentifier());
        addIfBlank(missing, "PATIENT_IDENTIFIER", snapshot.patientIdentifier());
        if (blank(snapshot.patientFirstName()) || blank(snapshot.patientLastName())) missing.add("PATIENT_NAME");
        addIfBlank(missing, "PATIENT_FISCAL_CODE", snapshot.patientFiscalCode());
        addIfBlank(missing, "DOCUMENT_TYPE", snapshot.documentType());
        addIfBlank(missing, "DEPARTMENT", snapshot.department());
        if (snapshot.producedAt() == null) missing.add("PRODUCED_AT");
        if (!snapshot.sourceSystemActive()) missing.add("SOURCE_SYSTEM_INACTIVE");
        if (snapshot.activeDocumentCount() == 0) missing.add("ACTIVE_DOCUMENT");
        return List.copyOf(missing);
    }

    private ReportState readinessState(List<String> missingFields) {
        boolean signerMissing = missingFields.stream().anyMatch(value -> value.startsWith("SIGNER"));
        if (signerMissing) return ReportState.MISSING_SIGNER;
        return missingFields.isEmpty() ? ReportState.READY_TO_SIGN : ReportState.INCOMPLETE;
    }

    private void ensureTransition(ReportState from, ReportState to) {
        if (!ReportWorkflowRules.canTransition(from, to)) {
            throw conflict("Transition from " + from + " to " + to + " is not allowed");
        }
    }

    private void requireVersion(ReportWorkflowSnapshot current, long expectedVersion) {
        if (current.version() != expectedVersion) {
            throw conflict("The report was updated by another operation; expected version " + expectedVersion
                    + " but current version is " + current.version());
        }
    }

    private boolean isAdminCorrectable(ReportState state) {
        return Set.of(ReportState.INCOMPLETE, ReportState.MISSING_SIGNER, ReportState.READY_TO_SIGN)
                .stream().anyMatch(target -> ReportWorkflowRules.canAdminCorrect(state, target));
    }

    private String fingerprint(ReportWorkflowOperation operation, UUID signerId, ReportState target, String reason) {
        String value = operation + "|" + Objects.toString(signerId, "") + "|"
                + Objects.toString(target, "") + "|" + Objects.toString(reason, "");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String operationKey(String value) {
        return requiredText(value, "Operation key is required");
    }

    private String actor(String value) {
        return requiredText(value, "Authenticated actor is required");
    }

    private String requiredText(String value, String message) {
        if (blank(value)) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private void addIfBlank(List<String> fields, String name, String value) {
        if (blank(value)) fields.add(name);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException unprocessable(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
