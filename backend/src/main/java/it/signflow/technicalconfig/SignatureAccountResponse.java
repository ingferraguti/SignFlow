package it.signflow.technicalconfig;

import java.util.UUID;

public record SignatureAccountResponse(
        UUID id, UUID applicationUserId, String applicationUsername, UUID signatureProviderId,
        String signatureProviderCode, String accountAlias, String providerUsername,
        String certificateAlias, boolean active) {
}
