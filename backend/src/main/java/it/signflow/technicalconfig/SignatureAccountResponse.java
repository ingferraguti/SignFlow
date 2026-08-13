package it.signflow.technicalconfig;

import java.util.UUID;

public record SignatureAccountResponse(
        UUID id, UUID applicationUserId, String applicationUsername, UUID naturalPersonId,
        UUID signatureProviderId,
        String signatureProviderCode, String accountAlias, String providerUsername,
        String certificateAlias, String displayName, String signatureType, boolean qualified, boolean active) {
}
