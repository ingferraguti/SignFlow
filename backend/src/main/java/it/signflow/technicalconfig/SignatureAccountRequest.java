package it.signflow.technicalconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SignatureAccountRequest(
        @NotNull UUID applicationUserId,
        @NotNull UUID signatureProviderId,
        @NotBlank @Size(max = 120) String accountAlias,
        @Size(max = 160) String providerUsername,
        @Size(max = 200) String certificateAlias,
        @Size(max = 160) String displayName,
        @Size(max = 40) String signatureType,
        boolean qualified,
        boolean active) {
}
