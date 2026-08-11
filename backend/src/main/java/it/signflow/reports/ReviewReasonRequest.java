package it.signflow.reports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewReasonRequest(
        long expectedVersion, @NotBlank @NotNull String operationKey,
        @NotBlank @NotNull @Size(max = 500) String reason) {
}
