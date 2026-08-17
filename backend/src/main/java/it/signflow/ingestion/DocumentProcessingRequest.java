package it.signflow.ingestion;

import java.util.UUID;

public record DocumentProcessingRequest(
        UUID reportId,
        byte[] content,
        String filename,
        String sourceSystemCode,
        String correlationId) {
    public DocumentProcessingRequest {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override public byte[] content() { return content.clone(); }
}
