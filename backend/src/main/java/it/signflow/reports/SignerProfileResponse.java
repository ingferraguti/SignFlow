package it.signflow.reports;

import java.util.List;
import java.util.UUID;

public record SignerProfileResponse(
        UUID id,
        String username,
        String firstName,
        String lastName,
        String email,
        String signerFiscalCode,
        UUID naturalPersonId,
        String identifierScheme,
        String issuingCountry,
        String maskedPersonalIdentifier,
        String partitionCode,
        String partitionName,
        String companyCode,
        String companyName,
        List<String> groups,
        List<AuthenticationAccountResponse> authenticationAccounts,
        List<DigitalSignatureOptionResponse> digitalSignatures) {
}
