package it.signflow.ingestion;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class MockDocumentNormalizer implements DocumentNormalizer {
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    @Override
    public DocumentProcessingResult normalize(DocumentProcessingRequest request) {
        byte[] content = request.content();
        if (content.length < PDF_HEADER.length) throw new IllegalArgumentException("Inbound document is not a PDF");
        for (int index = 0; index < PDF_HEADER.length; index++) {
            if (content[index] != PDF_HEADER[index]) throw new IllegalArgumentException("Inbound document is not a PDF");
        }
        return new DocumentProcessingResult(content,
                "MOCK ONLY - controllo strutturale, nessuna normalizzazione clinica reale");
    }
}
