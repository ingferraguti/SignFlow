package it.signflow.ingestion;

import java.util.List;

public record ServiceDocumentTypeCatalogResponse(
        ServiceDocumentType documentType,
        String displayName,
        List<Subtype> subtypes) {

    public record Subtype(String code, String displayName, String sourceReference) {
    }
}
