package it.signflow.ingestion;

/** Converts a document to PDF/A-3 when enabled by SourceSystem configuration. */
public interface PdfA3Converter {
    DocumentProcessingResult convert(DocumentProcessingRequest request);
}
