package it.signflow.technicalconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignatureProviderRequest(
        @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9._-]+") String code,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 80) String adapterType,
        @Size(max = 500) String baseUrl,
        @NotNull SignatureAuthenticationMode authenticationMode,
        @Size(max = 200) String credentialReference,
        boolean supportsVisibleSignature,
        boolean supportsMultipleSignature,
        boolean active) {
}
