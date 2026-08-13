package it.signflow.fse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

class PdfBoxCdaInjectorTest {
    @Test
    void embedsCdaUsingTheGatewayRequiredFilename() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }
        byte[] cda = "<ClinicalDocument xmlns=\"urn:hl7-org:v3\"/>".getBytes(StandardCharsets.UTF_8);

        byte[] result = new PdfBoxCdaInjector().inject(pdf, cda);

        try (PDDocument document = Loader.loadPDF(result)) {
            var embeddedFiles = document.getDocumentCatalog().getNames().getEmbeddedFiles().getNames();
            assertThat(embeddedFiles).containsKey("cda.xml");
            var specification = embeddedFiles.get("cda.xml");
            assertThat(specification.getEmbeddedFile().toByteArray()).isEqualTo(cda);
            assertThat(specification.getEmbeddedFile().getSubtype()).isEqualTo("application/xml");
        }
    }

    @Test
    void rejectsNonCdaAndUnsafeXml() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }
        PdfBoxCdaInjector injector = new PdfBoxCdaInjector();

        assertThatThrownBy(() -> injector.inject(pdf, "<root/>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CDA XML root must be hl7:ClinicalDocument");
        assertThatThrownBy(() -> injector.inject(pdf,
                "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///tmp/secret'>]><ClinicalDocument xmlns='urn:hl7-org:v3'/>"
                        .getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CDA XML must be well-formed and safe to parse");
    }
}
