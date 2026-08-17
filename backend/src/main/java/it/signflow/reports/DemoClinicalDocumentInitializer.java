package it.signflow.reports;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Component
@Order(10)
@ConditionalOnProperty(prefix = "signflow.documents", name = "demo-enabled", havingValue = "true")
public class DemoClinicalDocumentInitializer implements ApplicationRunner {
    private static final UUID DEMO_REPORT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
    private static final UUID READY_REPORT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
    private static final List<UUID> MOCK_SIGNATURE_REPORTS = List.of(
            UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1"),
            UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2"),
            UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3"),
            UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4"));
    private static final List<UUID> EXTERNAL_DELIVERY_REPORTS = List.of(
            UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff1"),
            UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff2"));
    private final ClinicalDocumentRepository repository;
    private final ClinicalDocumentService service;

    public DemoClinicalDocumentInitializer(ClinicalDocumentRepository repository, ClinicalDocumentService service) {
        this.repository = repository;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.list(DEMO_REPORT_ID, true).isEmpty()) {
            service.upload(DEMO_REPORT_ID, "referto-dimostrativo.pdf", demoPdf(), "demo.admin");
        }
        if (repository.list(READY_REPORT_ID, true).isEmpty()) {
            service.upload(READY_REPORT_ID, "referto-pronto-fittizio.pdf", demoPdf(), "demo.admin");
        }
        for (UUID reportId : MOCK_SIGNATURE_REPORTS) {
            if (repository.list(reportId, true).isEmpty()) {
                service.upload(reportId, "referto-firma-mock-totalmente-fittizio.pdf", demoPdf(), "demo.producer");
            }
        }
        for (UUID reportId : EXTERNAL_DELIVERY_REPORTS) {
            if (repository.list(reportId, true).isEmpty()) {
                service.upload(reportId, "referto-fse-conservazione-mock-fittizio.pdf", demoPdf(), "demo.producer");
            }
        }
    }

    public static byte[] demoPdf() {
        String content = "BT /F1 18 Tf 72 760 Td (SignFlow - referto totalmente fittizio) Tj ET\n";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + content.getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n" + content + "endstream");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.size());
            write(output, (index + 1) + " 0 obj\n" + objects.get(index) + "\nendobj\n");
        }
        int xrefOffset = output.size();
        write(output, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        offsets.forEach(offset -> write(output, String.format("%010d 00000 n \n", offset)));
        write(output, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xrefOffset + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }
}
