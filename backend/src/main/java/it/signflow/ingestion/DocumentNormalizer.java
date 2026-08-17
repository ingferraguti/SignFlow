package it.signflow.ingestion;

/** Normalizes inbound documents without binding the domain to one conversion library. */
public interface DocumentNormalizer {
    DocumentProcessingResult normalize(DocumentProcessingRequest request);
}
