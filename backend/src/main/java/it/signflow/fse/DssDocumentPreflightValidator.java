package it.signflow.fse;

import it.signflow.signatures.DigitalSignatureEngine;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DssDocumentPreflightValidator implements DocumentPreflightValidator {
    private final DigitalSignatureEngine signatureEngine;
    private final boolean allowMockSignatures;

    public DssDocumentPreflightValidator(DigitalSignatureEngine signatureEngine,
            @Value("${signflow.external-delivery.allow-mock-signatures:false}") boolean allowMockSignatures) {
        this.signatureEngine = signatureEngine;
        this.allowMockSignatures = allowMockSignatures;
    }

    @Override
    public ValidationResult validate(byte[] document, String signatureKind) {
        if (document == null || document.length < 5 || document[0] != '%' || document[1] != 'P'
                || document[2] != 'D' || document[3] != 'F' || document[4] != '-') {
            return new ValidationResult(false, "EU_DSS_6_4", "NOT_A_PDF", "Il documento non e un PDF");
        }
        if ("MOCK".equals(signatureKind) && allowMockSignatures) {
            return new ValidationResult(true, "EU_DSS_6_4_TEST_GATE",
                    "MOCK_SIGNATURE_TEST_ONLY", "Firma mock ammessa esclusivamente nel profilo locale di collaudo");
        }
        try {
            DigitalSignatureEngine.VerificationResult result = signatureEngine.verifyPdf(document, List.of());
            if (!result.signed()) {
                return new ValidationResult(false, "EU_DSS_6_4", "UNSIGNED_DOCUMENT",
                        "Il PDF non contiene una firma PAdES");
            }
            if (!result.valid()) {
                return new ValidationResult(false, "EU_DSS_6_4", "INVALID_PADES",
                        "La firma PAdES non supera la validazione tecnica");
            }
            return new ValidationResult(true, "EU_DSS_6_4", "VALID_PADES", "Firma PAdES tecnicamente valida");
        } catch (IllegalArgumentException exception) {
            return new ValidationResult(false, "EU_DSS_6_4", "VALIDATION_ERROR", exception.getMessage());
        }
    }
}
