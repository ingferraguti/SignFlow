package it.signflow.signatures;

import java.time.OffsetDateTime;
import java.util.List;

/** Provider-neutral cryptographic operations. Private keys never cross this boundary except as test key material. */
public interface DigitalSignatureEngine {
    PadesResult createTestPades(PadesRequest request);

    VerificationResult verifyPdf(byte[] document, List<byte[]> trustedCertificates);

    record PadesRequest(byte[] pdf, byte[] testPkcs12, char[] password, String documentName,
                        String reason, String location, String correlationId) {
    }

    record PadesResult(byte[] signedPdf, VerificationResult verification, String correlationId) {
    }

    record VerificationResult(boolean pdf, boolean signed, boolean valid, int signatureCount,
                              List<SignatureInformation> signatures) {
    }

    record SignatureInformation(String id, String format, String indication, String subIndication,
                                String signedBy, String subject, String issuer, String serialNumber,
                                String digestAlgorithm, OffsetDateTime signingTime,
                                OffsetDateTime certificateNotBefore, OffsetDateTime certificateNotAfter) {
    }
}
