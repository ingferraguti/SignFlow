package it.signflow.technicalconfig;

public record FseDocumentTypeResponse(
        String code,
        String displayName,
        String description,
        boolean active) {
}
