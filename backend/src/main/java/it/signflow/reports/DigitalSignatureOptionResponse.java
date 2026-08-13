package it.signflow.reports;

import java.util.UUID;

public record DigitalSignatureOptionResponse(UUID id, String displayName, String providerCode,
        String accountAlias, String certificateAlias, String signatureType, boolean qualified,
        boolean preferred) {
}
