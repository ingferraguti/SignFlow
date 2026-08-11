package it.signflow.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportWorkflowRulesTest {
    @Test
    void definesTheCompleteLifecycleAndRejectsSkippedTransitions() {
        Map<ReportState, Set<ReportState>> expected = Map.ofEntries(
                Map.entry(ReportState.RECEIVED, Set.of(ReportState.PARSED, ReportState.INCOMPLETE, ReportState.MISSING_SIGNER)),
                Map.entry(ReportState.PARSED, Set.of(ReportState.INCOMPLETE, ReportState.MISSING_SIGNER, ReportState.READY_TO_SIGN)),
                Map.entry(ReportState.INCOMPLETE, Set.of(ReportState.MISSING_SIGNER, ReportState.READY_TO_SIGN)),
                Map.entry(ReportState.MISSING_SIGNER, Set.of(ReportState.INCOMPLETE, ReportState.READY_TO_SIGN)),
                Map.entry(ReportState.READY_TO_SIGN, Set.of(ReportState.INCOMPLETE, ReportState.MISSING_SIGNER,
                        ReportState.PREVIEWED, ReportState.SIGN_BATCH_CREATED, ReportState.SIGNING)),
                Map.entry(ReportState.PREVIEWED, Set.of(ReportState.INCOMPLETE, ReportState.MISSING_SIGNER,
                        ReportState.REVIEW_PENDING, ReportState.SIGN_BATCH_CREATED, ReportState.SIGNING)),
                Map.entry(ReportState.REVIEW_PENDING, Set.of(ReportState.INCOMPLETE, ReportState.PREVIEWED, ReportState.APPROVED)),
                Map.entry(ReportState.APPROVED, Set.of(ReportState.REVIEW_PENDING, ReportState.SIGN_BATCH_CREATED, ReportState.SIGNING)),
                Map.entry(ReportState.SIGN_BATCH_CREATED, Set.of(ReportState.SIGNING, ReportState.SIGN_ERROR)),
                Map.entry(ReportState.SIGNING, Set.of(ReportState.SIGNED, ReportState.SIGN_ERROR)),
                Map.entry(ReportState.SIGN_ERROR, Set.of(ReportState.READY_TO_SIGN, ReportState.SIGNING)),
                Map.entry(ReportState.SIGNED, Set.of(ReportState.FSE_VALIDATION_ERROR, ReportState.FSE_SENT,
                        ReportState.CONSERVATION_SENT)),
                Map.entry(ReportState.FSE_VALIDATION_ERROR, Set.of(ReportState.FSE_SENT)),
                Map.entry(ReportState.FSE_SENT, Set.of(ReportState.FSE_ACCEPTED, ReportState.FSE_REJECTED)),
                Map.entry(ReportState.FSE_ACCEPTED, Set.of(ReportState.CONSERVATION_SENT)),
                Map.entry(ReportState.FSE_REJECTED, Set.of(ReportState.FSE_SENT)),
                Map.entry(ReportState.CONSERVATION_SENT, Set.of(ReportState.CONSERVATION_ACCEPTED)),
                Map.entry(ReportState.CONSERVATION_ACCEPTED, Set.of()));

        assertThat(expected).hasSize(ReportState.values().length);
        expected.forEach((state, targets) -> assertThat(ReportWorkflowRules.allowedTargets(state))
                .as("allowed targets from %s", state).containsExactlyInAnyOrderElementsOf(targets));
        assertThat(ReportWorkflowRules.canTransition(ReportState.RECEIVED, ReportState.SIGNED)).isFalse();
        assertThat(ReportWorkflowRules.canTransition(ReportState.SIGNED, ReportState.READY_TO_SIGN)).isFalse();
        assertThat(ReportWorkflowRules.canTransition(ReportState.CONSERVATION_ACCEPTED, ReportState.SIGNING)).isFalse();
    }

    @Test
    void limitsSpecialOperationsToTheirExplicitStateSets() {
        assertThat(ReportWorkflowRules.canAssignSigner(ReportState.MISSING_SIGNER)).isTrue();
        assertThat(ReportWorkflowRules.canAssignSigner(ReportState.SIGNED)).isFalse();
        assertThat(ReportWorkflowRules.canRegisterPreview(ReportState.READY_TO_SIGN)).isTrue();
        assertThat(ReportWorkflowRules.canRegisterPreview(ReportState.INCOMPLETE)).isFalse();
        assertThat(ReportWorkflowRules.canAdminCorrect(ReportState.SIGN_ERROR, ReportState.READY_TO_SIGN)).isTrue();
        assertThat(ReportWorkflowRules.canAdminCorrect(ReportState.SIGNED, ReportState.READY_TO_SIGN)).isFalse();
        assertThat(ReportWorkflowRules.canAdminCorrect(ReportState.PREVIEWED, ReportState.SIGNED)).isFalse();
    }
}
