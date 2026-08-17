package it.signflow.ingestion;

public record DocumentProcessingResult(byte[] content, String notice) {
    public DocumentProcessingResult {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override public byte[] content() { return content.clone(); }
}
