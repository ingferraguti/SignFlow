package it.signflow.reports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminCorrectionRequest(
        @NotNull ReportState targetState,
        @NotBlank @Size(max = 500) String reason,
        @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 120) String operationKey) {
}
