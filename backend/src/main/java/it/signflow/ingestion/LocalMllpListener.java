package it.signflow.ingestion;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class LocalMllpListener implements SmartLifecycle {
    private static final int VT = 0x0b;
    private static final int FS = 0x1c;
    private static final int CR = 0x0d;
    private final ReportIngestionService service;
    private final IngestionProperties properties;
    private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running;
    private volatile ServerSocket server;
    private volatile Thread acceptThread;

    LocalMllpListener(ReportIngestionService service, IngestionProperties properties) {
        this.service = service; this.properties = properties;
    }

    @Override public synchronized void start() {
        if (running || !properties.mllpEnabled()) return;
        try {
            server = new ServerSocket();
            server.bind(new InetSocketAddress(properties.mllpBindAddress(), properties.mllpPort()));
            running = true;
            acceptThread = Thread.ofPlatform().name("signflow-mllp-accept").daemon(true).start(this::acceptLoop);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to start the local MLLP listener", exception);
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = server.accept();
                clients.submit(() -> handle(socket));
            }
            catch (Exception exception) { if (running) stop(); }
        }
    }

    private void handle(Socket socket) {
        try (socket; InputStream input = socket.getInputStream(); OutputStream output = socket.getOutputStream()) {
            socket.setSoTimeout(15000);
            String raw = readFrame(input);
            String control = controlId(raw);
            IngestionResult result = service.ingestHl7(raw, null, control,
                    "mllp-" + safe(control) + "-" + UUID.randomUUID(), IngestionTransport.MLLP);
            writeFrame(output, ack(control, result.status() == Hl7MessageStatus.PROCESSED ? "AA" : "AE",
                    result.errorCode()));
        } catch (Exception ignored) {
            // Connection-level failures are deliberately not logged with payload or patient data.
        }
    }

    private String readFrame(InputStream input) throws Exception {
        if (input.read() != VT) throw new IllegalArgumentException("Invalid MLLP start block");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) >= 0) {
            if (next == FS) { input.read(); break; }
            if (output.size() >= properties.maxMessageBytes()) throw new IllegalArgumentException("MLLP frame too large");
            output.write(next);
        }
        if (next < 0) throw new IllegalArgumentException("Incomplete MLLP frame");
        return output.toString(StandardCharsets.UTF_8);
    }

    private void writeFrame(OutputStream output, String message) throws Exception {
        output.write(VT); output.write(message.getBytes(StandardCharsets.UTF_8)); output.write(FS); output.write(CR); output.flush();
    }

    private String ack(String controlId, String code, String errorCode) {
        String now = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String safeControl = safe(controlId);
        return "MSH|^~\\&|SIGNFLOW|LOCAL|SOURCE|LOCAL|" + now + "||ACK|ACK-" + safeControl + "|P|2.5\r"
                + "MSA|" + code + "|" + safeControl + (errorCode == null ? "" : "|" + safe(errorCode)) + "\r";
    }

    private String controlId(String raw) {
        if (raw == null) return "UNKNOWN";
        String first = raw.replace('\n', '\r').split("\r", 2)[0];
        String[] fields = first.split("\\|", -1);
        return fields.length > 9 && !fields[9].isBlank() ? fields[9] : "UNKNOWN";
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        String safe = value.replaceAll("[^A-Za-z0-9_.-]", "-");
        return safe.substring(0, Math.min(80, safe.length()));
    }

    @Override public synchronized void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        clients.shutdownNow();
    }
    @Override public void stop(Runnable callback) { stop(); callback.run(); }
    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }
    public int boundPort() { return server == null ? -1 : server.getLocalPort(); }
}
