package it.signflow.technicalconfig;

import java.util.UUID;

public record SourceSystemFseDocumentTypeResponse(
        UUID sourceSystemId,
        String sourceSystemCode,
        String documentTypeCode,
        String documentTypeName,
        boolean cdaInjectionEnabled) {
}
