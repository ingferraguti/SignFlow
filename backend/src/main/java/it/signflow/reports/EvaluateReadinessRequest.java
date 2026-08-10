package it.signflow.reports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EvaluateReadinessRequest(
        @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 120) String operationKey) {
}
