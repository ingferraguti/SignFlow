package it.signflow.ingestion;

import org.springframework.stereotype.Component;

@Component
public class MockPdfA3Converter implements PdfA3Converter {
    @Override
    public DocumentProcessingResult convert(DocumentProcessingRequest request) {
        return new DocumentProcessingResult(request.content(),
                "MOCK ONLY - il documento non costituisce una conversione PDF/A-3 certificata");
    }
}
