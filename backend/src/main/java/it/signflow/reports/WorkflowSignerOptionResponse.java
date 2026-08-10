package it.signflow.reports;

import java.util.UUID;

public record WorkflowSignerOptionResponse(
        UUID id,
        String username,
        String displayName,
        String signerFiscalCode) {
}
