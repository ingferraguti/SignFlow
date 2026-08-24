package it.signflow.signatures;

import eu.europa.esig.dss.diagnostic.CertificateWrapper;
import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.MimeTypeEnum;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxNativeObjectFactory;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import java.security.KeyStore.PasswordProtection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DssPadesSignatureEngine implements DigitalSignatureEngine {
    private static final String TEST_REASON = "Firma PAdES di collaudo - nessun valore legale";

    @Override
    public PadesResult createTestPades(PadesRequest request) {
        requirePdf(request.pdf());
        if (request.testPkcs12() == null || request.testPkcs12().length == 0) {
            throw new IllegalArgumentException("A test PKCS#12 is required");
        }
        char[] password = request.password() == null ? new char[0] : request.password().clone();
        try (Pkcs12SignatureToken token = new Pkcs12SignatureToken(request.testPkcs12(),
                new PasswordProtection(password))) {
            DSSPrivateKeyEntry privateKey = token.getKeys().stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("The test PKCS#12 has no signing key"));
            PAdESSignatureParameters parameters = new PAdESSignatureParameters();
            parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
            parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
            parameters.setSigningCertificate(privateKey.getCertificate());
            parameters.setCertificateChain(privateKey.getCertificateChain());
            parameters.setReason(blankToDefault(request.reason(), TEST_REASON));
            parameters.setLocation(blankToDefault(request.location(), "Ambiente locale SignFlow"));

            DSSDocument source = new InMemoryDocument(request.pdf(),
                    blankToDefault(request.documentName(), "documento-fittizio.pdf"), MimeTypeEnum.PDF);
            PAdESService service = new PAdESService(new CommonCertificateVerifier());
            service.setPdfObjFactory(new PdfBoxNativeObjectFactory());
            ToBeSigned dataToSign = service.getDataToSign(source, parameters);
            SignatureValue signatureValue = token.sign(dataToSign, DigestAlgorithm.SHA256, privateKey);
            if (!service.isValidSignatureValue(dataToSign, signatureValue, privateKey.getCertificate())) {
                throw new IllegalStateException("DSS rejected the locally produced signature value");
            }
            byte[] signed = DSSUtils.toByteArray(service.signDocument(source, parameters, signatureValue));
            VerificationResult verification = verifyPdf(signed, List.of(privateKey.getCertificate().getEncoded()));
            if (!verification.valid()) throw new IllegalStateException("DSS did not validate the produced test PAdES");
            return new PadesResult(signed, verification, request.correlationId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to create the test PAdES", exception);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    @Override
    public VerificationResult verifyPdf(byte[] document, List<byte[]> trustedCertificates) {
        return verifyPdfAgainst(document, null, trustedCertificates);
    }

    @Override
    public VerificationResult verifyPdfAgainst(byte[] document, byte[] expectedUnsignedDocument,
                                               List<byte[]> trustedCertificates) {
        requirePdf(document);
        try {
            DSSDocument pdf = new InMemoryDocument(document, "documento-da-verificare.pdf", MimeTypeEnum.PDF);
            SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(pdf);
            CommonCertificateVerifier verifier = new CommonCertificateVerifier();
            if (trustedCertificates != null && !trustedCertificates.isEmpty()) {
                CommonTrustedCertificateSource trusted = new CommonTrustedCertificateSource();
                trustedCertificates.forEach(encoded -> trusted.addCertificate(DSSUtils.loadCertificate(encoded)));
                verifier.setTrustedCertSources(trusted);
            }
            validator.setCertificateVerifier(verifier);
            Reports reports = validator.validateDocument();
            SimpleReport simple = reports.getSimpleReport();
            DiagnosticData diagnostic = reports.getDiagnosticData();
            List<SignatureInformation> information = simple.getSignatureIdList().stream()
                    .map(id -> signatureInformation(id, simple, diagnostic)).toList();
            boolean technicallyValid = !information.isEmpty() && simple.getSignatureIdList().stream()
                    .allMatch(diagnostic::isBLevelTechnicallyValid);
            boolean valid = !information.isEmpty() && simple.getSignatureIdList().stream().allMatch(simple::isValid);
            boolean originalMatches = expectedUnsignedDocument == null || simple.getSignatureIdList().stream()
                    .flatMap(id -> validator.getOriginalDocuments(id).stream())
                    .map(DSSUtils::toByteArray)
                    .anyMatch(original -> java.util.Arrays.equals(original, expectedUnsignedDocument));
            return new VerificationResult(true, !information.isEmpty(), technicallyValid, valid, originalMatches,
                    information.size(), information);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unable to verify the PDF with DSS", exception);
        }
    }

    private SignatureInformation signatureInformation(String id, SimpleReport simple, DiagnosticData diagnostic) {
        String certificateId = diagnostic.getSigningCertificateId(id);
        CertificateWrapper certificate = certificateId == null ? null : diagnostic.getUsedCertificateByIdNullSafe(certificateId);
        return new SignatureInformation(id, value(simple.getSignatureFormat(id)), value(simple.getIndication(id)),
                value(simple.getSubIndication(id)), simple.getSignedBy(id),
                certificate == null ? null : certificate.getCertificateDN(),
                certificate == null ? null : certificate.getCertificateIssuerDN(),
                certificate == null ? null : certificate.getSerialNumber(),
                value(diagnostic.getSignatureDigestAlgorithm(id)), toOffset(simple.getSigningTime(id)),
                certificate == null ? null : toOffset(certificate.getNotBefore()),
                certificate == null ? null : toOffset(certificate.getNotAfter()));
    }

    private void requirePdf(byte[] document) {
        if (document == null || document.length < 5 || document[0] != '%' || document[1] != 'P'
                || document[2] != 'D' || document[3] != 'F' || document[4] != '-') {
            throw new IllegalArgumentException("The document is not a PDF");
        }
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String value(Object value) {
        return value == null ? null : value.toString();
    }

    private OffsetDateTime toOffset(Date value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }
}
