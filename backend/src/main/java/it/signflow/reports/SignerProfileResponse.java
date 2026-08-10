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
        String partitionCode,
        String partitionName,
        String companyCode,
        String companyName,
        List<String> groups) {
}
