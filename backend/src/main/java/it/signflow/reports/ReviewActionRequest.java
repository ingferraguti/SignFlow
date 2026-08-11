package it.signflow.reports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewActionRequest(long expectedVersion, @NotBlank @NotNull String operationKey) {
}
