package it.signflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.signflow.signatures.DigitalSignatureEngine;
import it.signflow.signatures.DssPadesSignatureEngine;
import it.signflow.signatures.TestSignatureFixtures;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServicePdfA3NormalizerTest {
    private static TestSignatureFixtures.TestKeyMaterial keyMaterial;
    private final ServicePdfA3Normalizer normalizer = new ServicePdfA3Normalizer(144, 20, 20_000_000);

    @BeforeAll
    static void createCertificate() {
        keyMaterial = TestSignatureFixtures.testKeyMaterial();
    }

    @Test
    void convertsPdfImageAndUtf8TextToValidatedPdfA3B() throws Exception {
        byte[] pdf = pdf20(TestSignatureFixtures.fictionalPdf());
        var pdfResult = normalizer.normalize(pdf, "application/x-pdf", "input-v1.pdf", 5_000_000);
        var pngResult = normalizer.normalize(image("png"), "image/png", "scan.png", 5_000_000);
        var jpegResult = normalizer.normalize(image("jpeg"), "image/jpeg", "scan.jpg", 5_000_000);
        var tiffResult = normalizer.normalize(image("tiff"), "image/tiff", "scan.tiff", 5_000_000);
        var textResult = normalizer.normalize("Contenuto amministrativo fittizio\nSeconda riga"
                .getBytes(StandardCharsets.UTF_8), "text/plain; charset=UTF-8", "nota.txt", 5_000_000);

        for (var result : new ServicePdfA3Normalizer.NormalizedDocument[]{
                pdfResult, pngResult, jpegResult, tiffResult, textResult}) {
            assertThat(result.profile()).isEqualTo("PDF/A-3B");
            assertThat(result.pdfaPart()).isEqualTo("3");
            assertThat(result.pdfaConformance()).isEqualTo("B");
            assertThat(result.validator()).startsWith("veraPDF");
            assertThat(result.pageCount()).isEqualTo(1);
            try (var document = Loader.loadPDF(result.content())) {
                assertThat(document.getVersion()).isEqualTo(1.7f);
                assertThat(document.getDocumentCatalog().getMetadata()).isNotNull();
                assertThat(document.getDocumentCatalog().getOutputIntents()).hasSize(1);
            }
        }
    }

    @Test
    void rejectsEncryptedAndAlreadySignedPdf() throws Exception {
        assertThatThrownBy(() -> normalizer.normalize(encryptedPdf(), "application/pdf",
                "encrypted.pdf", 5_000_000))
                .hasMessageContaining("password-protected");

        var signed = new DssPadesSignatureEngine().createTestPades(new DigitalSignatureEngine.PadesRequest(
                TestSignatureFixtures.fictionalPdf(), keyMaterial.pkcs12(), TestSignatureFixtures.PASSWORD,
                "already-signed.pdf", "Firma di test", "Ambiente locale", "normalizer-signed-input"));
        assertThatThrownBy(() -> normalizer.normalize(signed.signedPdf(), "application/pdf",
                "already-signed.pdf", 5_000_000))
                .hasMessageContaining("Already signed PDFs cannot be normalized");
    }

    private byte[] image(String format) throws Exception {
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 600, 400);
        graphics.setColor(Color.BLUE); graphics.drawString("Scansione fittizia", 80, 180); graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).as("ImageIO writer for %s", format).isTrue();
        return output.toByteArray();
    }

    private byte[] encryptedPdf() throws Exception {
        try (var document = Loader.loadPDF(TestSignatureFixtures.fictionalPdf())) {
            var policy = new StandardProtectionPolicy("owner-test", "user-test", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] pdf20(byte[] source) throws Exception {
        try (var document = Loader.loadPDF(source)) {
            document.setVersion(2.0f);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }
}
