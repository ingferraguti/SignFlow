package it.signflow.identity;

import java.util.List;
import java.util.UUID;

public record ApplicationUserResponse(
        UUID id,
        String username,
        String oidcSubject,
        String firstName,
        String lastName,
        String email,
        String fiscalCode,
        String signerFiscalCode,
        String counterSignerFiscalCode,
        boolean active,
        OptionResponse partition,
        OptionResponse company,
        List<OptionResponse> roles,
        List<OptionResponse> groups) {
}
