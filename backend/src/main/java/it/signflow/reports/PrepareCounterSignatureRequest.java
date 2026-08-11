package it.signflow.reports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PrepareCounterSignatureRequest(
        long expectedVersion, @NotBlank @NotNull String operationKey, UUID counterSignerId) {
}
