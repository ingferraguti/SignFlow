package it.signflow.technicalconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SourceSystemFseDocumentTypeRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String documentTypeCode,
        boolean cdaInjectionEnabled) {
}
