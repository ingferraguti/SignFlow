package it.signflow.technicalconfig;

import java.util.UUID;

public record SignatureProviderResponse(
        UUID id, String code, String name, String adapterType, String baseUrl,
        SignatureAuthenticationMode authenticationMode, String credentialReference,
        boolean supportsVisibleSignature, boolean supportsMultipleSignature, boolean active) {
}
