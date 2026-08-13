package it.signflow.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record ApplicationUserRequest(
        @NotBlank @Size(max = 120) String username,
        @NotBlank @Size(max = 160) String oidcSubject,
        @NotBlank @Size(max = 120) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @Size(max = 200) String email,
        @Size(max = 32) String fiscalCode,
        @Size(max = 32) String signerFiscalCode,
        @Size(max = 32) String counterSignerFiscalCode,
        @Size(max = 40) String identifierScheme,
        @Size(max = 2) String issuingCountry,
        @Size(max = 200) String identifierIssuer,
        @Size(max = 200) String personalIdentifier,
        @Size(max = 300) String authenticationIssuer,
        @Size(max = 40) String authenticationMethod,
        @Size(max = 500) String identityCorrectionReason,
        boolean active,
        @NotNull UUID partitionId,
        @NotNull UUID companyId,
        Set<UUID> roleIds,
        Set<UUID> groupIds) {
}
