package it.signflow.reports;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

final class ReportWorkflowRules {
    private static final Map<ReportState, Set<ReportState>> TRANSITIONS = transitions();
    private static final Set<ReportState> ASSIGNABLE = EnumSet.of(
            ReportState.RECEIVED, ReportState.PARSED, ReportState.INCOMPLETE,
            ReportState.MISSING_SIGNER, ReportState.READY_TO_SIGN);
    private static final Set<ReportState> READINESS_CHECKABLE = EnumSet.copyOf(ASSIGNABLE);
    private static final Set<ReportState> PREVIEWABLE = EnumSet.of(
            ReportState.READY_TO_SIGN, ReportState.PREVIEWED, ReportState.REVIEW_PENDING,
            ReportState.APPROVED, ReportState.SIGN_BATCH_CREATED, ReportState.SIGNING,
            ReportState.SIGNED, ReportState.SIGN_ERROR, ReportState.FSE_VALIDATION_ERROR,
            ReportState.FSE_SENT, ReportState.FSE_ACCEPTED, ReportState.FSE_REJECTED,
            ReportState.CONSERVATION_SENT, ReportState.CONSERVATION_ACCEPTED);
    private static final Set<ReportState> ADMIN_CORRECTABLE = EnumSet.of(
            ReportState.RECEIVED, ReportState.PARSED, ReportState.INCOMPLETE,
            ReportState.MISSING_SIGNER, ReportState.READY_TO_SIGN, ReportState.PREVIEWED,
            ReportState.REVIEW_PENDING, ReportState.APPROVED, ReportState.SIGN_ERROR);
    private static final Set<ReportState> ADMIN_CORRECTION_TARGETS = EnumSet.of(
            ReportState.INCOMPLETE, ReportState.MISSING_SIGNER, ReportState.READY_TO_SIGN);

    private ReportWorkflowRules() {
    }

    static boolean canTransition(ReportState from, ReportState to) {
        return from == to || TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    static boolean canAssignSigner(ReportState state) {
        return ASSIGNABLE.contains(state);
    }

    static boolean canEvaluateReadiness(ReportState state) {
        return READINESS_CHECKABLE.contains(state);
    }

    static boolean canRegisterPreview(ReportState state) {
        return PREVIEWABLE.contains(state);
    }

    static boolean canAdminCorrect(ReportState from, ReportState to) {
        return ADMIN_CORRECTABLE.contains(from) && ADMIN_CORRECTION_TARGETS.contains(to);
    }

    static Set<ReportState> allowedTargets(ReportState state) {
        return Set.copyOf(TRANSITIONS.getOrDefault(state, Set.of()));
    }

    private static Map<ReportState, Set<ReportState>> transitions() {
        Map<ReportState, Set<ReportState>> rules = new EnumMap<>(ReportState.class);
        rules.put(ReportState.RECEIVED, states(ReportState.PARSED, ReportState.INCOMPLETE, ReportState.MISSING_SIGNER));
        rules.put(ReportState.PARSED, states(ReportState.INCOMPLETE, ReportState.MISSING_SIGNER, ReportState.READY_TO_SIGN));
        rules.put(ReportState.INCOMPLETE, states(ReportState.MISSING_SIGNER, ReportState.READY_TO_SIGN));
        rules.put(ReportState.MISSING_SIGNER, states(ReportState.INCOMPLETE, ReportState.READY_TO_SIGN));
        rules.put(ReportState.READY_TO_SIGN, states(ReportState.INCOMPLETE, ReportState.MISSING_SIGNER,
                ReportState.PREVIEWED, ReportState.SIGN_BATCH_CREATED, ReportState.SIGNING));
        rules.put(ReportState.PREVIEWED, states(ReportState.INCOMPLETE, ReportState.MISSING_SIGNER,
                ReportState.REVIEW_PENDING, ReportState.SIGN_BATCH_CREATED, ReportState.SIGNING));
        rules.put(ReportState.REVIEW_PENDING, states(ReportState.INCOMPLETE, ReportState.APPROVED));
        rules.put(ReportState.APPROVED, states(ReportState.SIGN_BATCH_CREATED, ReportState.SIGNING));
        rules.put(ReportState.SIGN_BATCH_CREATED, states(ReportState.SIGNING, ReportState.SIGN_ERROR));
        rules.put(ReportState.SIGNING, states(ReportState.SIGNED, ReportState.SIGN_ERROR));
        rules.put(ReportState.SIGN_ERROR, states(ReportState.READY_TO_SIGN, ReportState.SIGNING));
        rules.put(ReportState.SIGNED, states(ReportState.FSE_VALIDATION_ERROR, ReportState.FSE_SENT,
                ReportState.CONSERVATION_SENT));
        rules.put(ReportState.FSE_VALIDATION_ERROR, states(ReportState.FSE_SENT));
        rules.put(ReportState.FSE_SENT, states(ReportState.FSE_ACCEPTED, ReportState.FSE_REJECTED));
        rules.put(ReportState.FSE_REJECTED, states(ReportState.FSE_SENT));
        rules.put(ReportState.FSE_ACCEPTED, states(ReportState.CONSERVATION_SENT));
        rules.put(ReportState.CONSERVATION_SENT, states(ReportState.CONSERVATION_ACCEPTED));
        rules.put(ReportState.CONSERVATION_ACCEPTED, Set.of());
        return Map.copyOf(rules);
    }

    private static Set<ReportState> states(ReportState first, ReportState... remaining) {
        EnumSet<ReportState> values = EnumSet.of(first, remaining);
        return Set.copyOf(values);
    }
}
