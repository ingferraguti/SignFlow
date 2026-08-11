package it.signflow.signatures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DssPadesSignatureEngineTest {
    private static TestSignatureFixtures.TestKeyMaterial keyMaterial;
    private final DssPadesSignatureEngine engine = new DssPadesSignatureEngine();

    @BeforeAll
    static void createCertificate() {
        keyMaterial = TestSignatureFixtures.testKeyMaterial();
    }

    @Test
    void createsAndValidatesTestPadesBaselineBAndExtractsEssentialInformation() {
        byte[] source = TestSignatureFixtures.fictionalPdf();
        var unsigned = engine.verifyPdf(source, List.of(keyMaterial.certificate()));
        assertThat(unsigned.pdf()).isTrue();
        assertThat(unsigned.signed()).isFalse();
        assertThat(unsigned.signatureCount()).isZero();

        var result = engine.createTestPades(new DigitalSignatureEngine.PadesRequest(source,
                keyMaterial.pkcs12(), TestSignatureFixtures.PASSWORD, "referto-fittizio.pdf",
                "Firma esclusivamente di test", "Ambiente locale", "corr-pades-001"));

        assertThat(result.correlationId()).isEqualTo("corr-pades-001");
        assertThat(new String(result.signedPdf(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(result.verification().valid()).isTrue();
        assertThat(result.verification().signatureCount()).isEqualTo(1);
        var signature = result.verification().signatures().get(0);
        assertThat(signature.format()).isEqualTo("PAdES-BASELINE-B");
        assertThat(signature.indication()).isEqualTo("TOTAL_PASSED");
        assertThat(signature.subject()).contains("SignFlow Test Signer");
        assertThat(signature.issuer()).contains("SignFlow Test Signer");
        assertThat(signature.serialNumber()).isNotBlank();
        assertThat(signature.digestAlgorithm()).isEqualTo("SHA256");
        assertThat(signature.signingTime()).isNotNull();
        assertThat(signature.certificateNotAfter()).isAfter(signature.certificateNotBefore());
    }

    @Test
    void rejectsNonPdfAndWrongTestKeyPassword() {
        assertThatThrownBy(() -> engine.verifyPdf("not-a-pdf".getBytes(StandardCharsets.UTF_8), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a PDF");
        assertThatThrownBy(() -> engine.createTestPades(new DigitalSignatureEngine.PadesRequest(
                TestSignatureFixtures.fictionalPdf(), keyMaterial.pkcs12(), "wrong".toCharArray(),
                "fittizio.pdf", null, null, "corr-wrong-password")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unable to create");
    }
}
