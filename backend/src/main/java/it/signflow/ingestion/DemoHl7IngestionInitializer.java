package it.signflow.ingestion;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

@Component
final class DemoHl7IngestionInitializer {
    private static final String DEMO_PDF = Base64.getEncoder().encodeToString(("""
            %PDF-1.4
            1 0 obj
            << /Type /Catalog >>
            endobj
            %%EOF
            """).getBytes(StandardCharsets.US_ASCII));
    private final ReportIngestionService service;
    private final IngestionProperties properties;

    DemoHl7IngestionInitializer(ReportIngestionService service, IngestionProperties properties) {
        this.service = service; this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void initialize() {
        if (!properties.demoEnabled()) return;
        ingest(oru("LIS-DEMO", "HL7-DEMO-ORU-001", "RPT-HL7-DEMO-001", true), "demo-oru-001");
        ingest(mdm("DOC-DEMO", "HL7-DEMO-MDM-001", "RPT-HL7-DEMO-002"), "demo-mdm-001");
        ingest(oru("SOURCE-NOT-CONFIGURED", "HL7-DEMO-REJECT-001", "RPT-HL7-DEMO-REJECT", false),
                "demo-rejected-001");
    }

    private void ingest(String raw, String correlation) {
        service.ingestHl7(raw, null, correlation, correlation, IngestionTransport.REST);
    }

    static String oru(String source, String controlId, String reportIdentifier, boolean signer) {
        StringBuilder value = new StringBuilder();
        value.append(segment("MSH", Map.of(2, "^~\\&", 3, source, 4, "DEMO-FACILITY", 5, "SIGNFLOW",
                7, "20260815091000", 9, "ORU^R01", 10, controlId, 11, "P", 12, "2.5")));
        value.append(segment("PID", Map.of(3, "PAT-HL7-DEMO-001^^^DEMO^MR", 5, "Fittizia^Chiara",
                7, "19850101", 8, "F", 19, "TSTCHR85A41H501Q")));
        value.append(segment("PV1", Map.of(10, "LAB-DEMO", 19, "EP-HL7-DEMO-001")));
        value.append(segment("OBR", Map.of(3, reportIdentifier, 4, "REF^Referto fittizio",
                7, "20260815090000", 22, "20260815090500", 24, "LAB-DEMO")));
        if (signer) value.append(segment("ZSF", Map.of(1, "DMSLGN80A01H501U")));
        value.append(segment("OBX", Map.of(1, "1", 2, "ED", 3, "DOC^referto-demo.pdf",
                5, "^application/pdf^PDF^Base64^" + DEMO_PDF, 11, "F")));
        return value.toString();
    }

    static String mdm(String source, String controlId, String reportIdentifier) {
        return segment("MSH", Map.of(2, "^~\\&", 3, source, 4, "DEMO-FACILITY", 5, "SIGNFLOW",
                7, "20260815101500", 9, "MDM^T02", 10, controlId, 11, "P", 12, "2.5"))
                + segment("PID", Map.of(3, "PAT-HL7-DEMO-002^^^DEMO^MR", 5, "Fittizio^Leone",
                    7, "19790202", 8, "M", 19, "TSTLNE79B02H501R"))
                + segment("PV1", Map.of(10, "DOC-DEMO", 19, "EP-HL7-DEMO-002"))
                + segment("TXA", Map.of(2, "REF", 4, "20260815100000", 12, reportIdentifier, 17, "DOC-DEMO"))
                + segment("OBX", Map.of(1, "1", 2, "ED", 3, "DOC^documento-demo.pdf",
                    5, "^application/pdf^PDF^Base64^" + DEMO_PDF, 11, "F"));
    }

    private static String segment(String name, Map<Integer, String> fields) {
        int maximum = fields.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        StringBuilder result = new StringBuilder(name);
        int firstField = 1;
        if ("MSH".equals(name)) {
            result.append('|').append(fields.getOrDefault(2, "^~\\&"));
            firstField = 3;
        }
        for (int field = firstField; field <= maximum; field++) result.append('|').append(fields.getOrDefault(field, ""));
        return result.append('\r').toString();
    }
}
