package it.signflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.hl7v2.DefaultHapiContext;
import org.junit.jupiter.api.Test;

class Hl7V2ParserTest {
    private final Hl7V2Parser parser = new Hl7V2Parser(new DefaultHapiContext());

    @Test
    void parsesFictionalOruWithPatientSignerAndEmbeddedDocument() {
        ParsedHl7Message result = parser.parse(DemoHl7IngestionInitializer.oru(
                "LIS-DEMO", "PARSER-ORU-001", "RPT-PARSER-ORU-001", true));

        assertThat(result.sourceSystemCode()).isEqualTo("LIS-DEMO");
        assertThat(result.messageType()).isEqualTo("ORU");
        assertThat(result.triggerEvent()).isEqualTo("R01");
        assertThat(result.controlId()).isEqualTo("PARSER-ORU-001");
        assertThat(result.patientIdentifier()).isEqualTo("PAT-HL7-DEMO-001");
        assertThat(result.patientFirstName()).isEqualTo("Chiara");
        assertThat(result.patientLastName()).isEqualTo("Fittizia");
        assertThat(result.signerIdentifier()).isEqualTo("DMSLGN80A01H501U");
        assertThat(result.document()).startsWith("%PDF-".getBytes());
        assertThat(result.documentFilename()).isEqualTo("referto-demo.pdf");
    }

    @Test
    void parsesFictionalMdmAndLeavesSignerAbsent() {
        ParsedHl7Message result = parser.parse(DemoHl7IngestionInitializer.mdm(
                "DOC-DEMO", "PARSER-MDM-001", "RPT-PARSER-MDM-001"));

        assertThat(result.messageType()).isEqualTo("MDM");
        assertThat(result.triggerEvent()).isEqualTo("T02");
        assertThat(result.externalReportIdentifier()).isEqualTo("RPT-PARSER-MDM-001");
        assertThat(result.documentType()).isEqualTo("REF");
        assertThat(result.signerIdentifier()).isNull();
        assertThat(result.document()).isNotEmpty();
    }

    @Test
    void rejectsUnsupportedOrInvalidMessagesWithoutEchoingSensitiveContent() {
        String unsupported = DemoHl7IngestionInitializer.oru(
                "LIS-DEMO", "PARSER-ADT-001", "RPT-PARSER-ADT-001", true)
                .replace("ORU^R01", "ADT^A01");
        assertThatThrownBy(() -> parser.parse(unsupported))
                .isInstanceOf(Hl7ParsingException.class)
                .hasMessage("Only ORU and MDM messages are supported");
        assertThatThrownBy(() -> parser.parse("not-hl7|secret-value"))
                .isInstanceOf(Hl7ParsingException.class)
                .hasMessage("HL7 v2 parsing or initial validation failed");
    }
}
