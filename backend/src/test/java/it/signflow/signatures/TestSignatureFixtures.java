package it.signflow.signatures;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class TestSignatureFixtures {
    public static final char[] PASSWORD = "signflow-test-only".toCharArray();

    private TestSignatureFixtures() {
    }

    public static TestKeyMaterial testKeyMaterial() {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            X500Name subject = new X500Name("CN=SignFlow Test Signer,OU=Testing Only,O=SignFlow Fictional,C=IT");
            Instant now = Instant.now();
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject,
                    new BigInteger("1100110011001100"), Date.from(now.minus(1, ChronoUnit.DAYS)),
                    Date.from(now.plus(365, ChronoUnit.DAYS)), subject, keyPair.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
            X509Certificate certificate = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA")
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(keyPair.getPrivate())));
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, PASSWORD);
            store.setKeyEntry("signflow-test-signer", keyPair.getPrivate(), PASSWORD,
                    new java.security.cert.Certificate[]{certificate});
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            store.store(output, PASSWORD);
            return new TestKeyMaterial(output.toByteArray(), certificate.getEncoded());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create fictional test certificate", exception);
        }
    }

    public static byte[] fictionalPdf() {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                content.newLineAtOffset(72, 720);
                content.showText("SignFlow - documento clinico totalmente fittizio per test PAdES");
                content.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create fictional PDF", exception);
        }
    }

    public record TestKeyMaterial(byte[] pkcs12, byte[] certificate) {
    }
}
