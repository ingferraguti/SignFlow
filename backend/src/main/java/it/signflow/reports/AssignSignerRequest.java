package it.signflow.reports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AssignSignerRequest(
        UUID signerId,
        @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 120) String operationKey) {
}
