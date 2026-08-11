package it.signflow.reports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConfigureReviewRequest(
        UUID approverId, boolean separationRequired, boolean counterSignatureRequired,
        UUID counterSignerId, long expectedVersion, @NotBlank @NotNull String operationKey) {
}
