package it.signflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalMllpListenerTest {
    @Test
    void acceptsAnMllpFrameAndReturnsAStandardPositiveAcknowledgement() throws Exception {
        ReportIngestionService service = mock(ReportIngestionService.class);
        IngestionProperties properties = new IngestionProperties(
                1024 * 1024, 30, true, "127.0.0.1", 0, false);
        LocalMllpListener listener = new LocalMllpListener(service, properties);
        String raw = DemoHl7IngestionInitializer.oru(
                "LIS-DEMO", "MLLP-UNIT-001", "RPT-MLLP-UNIT-001", true);
        when(service.ingestHl7(eq(raw), eq(null), eq("MLLP-UNIT-001"), any(), eq(IngestionTransport.MLLP)))
                .thenReturn(new IngestionResult(UUID.randomUUID(), Hl7MessageStatus.PROCESSED,
                        UUID.randomUUID(), UUID.randomUUID(), it.signflow.reports.ReportState.READY_TO_SIGN,
                        "mllp-unit", null, null, false));

        try {
            listener.start();
            try (Socket socket = new Socket("127.0.0.1", listener.boundPort())) {
                socket.getOutputStream().write(0x0b);
                socket.getOutputStream().write(raw.getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().write(0x1c);
                socket.getOutputStream().write(0x0d);
                socket.getOutputStream().flush();
                ByteArrayOutputStream response = new ByteArrayOutputStream();
                int value;
                while ((value = socket.getInputStream().read()) >= 0 && value != 0x1c) {
                    if (value != 0x0b) response.write(value);
                }
                assertThat(response.toString(StandardCharsets.UTF_8))
                        .contains("MSA|AA|MLLP-UNIT-001")
                        .doesNotContain("PAT-HL7-DEMO-001");
            }
        } finally {
            listener.stop();
        }
    }
}
