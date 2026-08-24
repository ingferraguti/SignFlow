package it.signflow.ingestion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ServiceSignatureRequestInput(
        @NotBlank @Size(max = 120) String externalRequestId,
        @NotNull ServiceDocumentType documentType,
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[A-Z][A-Z0-9_]+$") String documentSubtype,
        @NotNull @Valid SignerIdentifier signer) {

    public record SignerIdentifier(
            @NotNull PersonIdentifierScheme scheme,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$") String issuingCountry,
            @NotBlank @Size(max = 120) String issuer,
            @NotBlank @Size(max = 240) String value) {
    }
}
