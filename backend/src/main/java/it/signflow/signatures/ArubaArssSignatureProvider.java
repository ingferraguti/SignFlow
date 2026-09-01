package it.signflow.signatures;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/** Aruba ARSS SOAP adapter. Aruba details are deliberately contained in this class. */
@Component
public final class ArubaArssSignatureProvider implements SignatureProviderAdapter {
    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String ARUBA_NS = "http://arubasignservice.arubapec.it/";
    private static final Duration SESSION_DURATION = Duration.ofMinutes(5);
    private final ArubaSignatureProperties properties;
    private final HttpClient client;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, String> idempotency = new ConcurrentHashMap<>();

    @Autowired
    ArubaArssSignatureProvider(ArubaSignatureProperties properties) {
        this(properties, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    ArubaArssSignatureProvider(ArubaSignatureProperties properties, HttpClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override public String adapterType() { return "ARUBA_ARSS"; }

    @Override
    public SessionResult openSession(OpenSessionCommand command) {
        requireTimeout(command.timeout(), command.correlationId());
        requireConfigured(command.correlationId());
        String reference = "aruba-session-" + UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(SESSION_DURATION);
        sessions.put(reference, new Session(expiresAt, null, null));
        return new SessionResult(reference, expiresAt, command.correlationId());
    }

    @Override
    public ChallengeResult requestChallenge(ChallengeCommand command) {
        requireTimeout(command.timeout(), command.correlationId());
        Session session = session(command.sessionReference(), command.correlationId());
        String challenge = "aruba-otp-" + UUID.randomUUID();
        sessions.put(command.sessionReference(), new Session(session.expiresAt(), challenge, null));
        return new ChallengeResult(challenge, "OTP", session.expiresAt(), command.correlationId());
    }

    @Override
    public AuthenticationResult authenticate(AuthenticationCommand command) {
        requireTimeout(command.timeout(), command.correlationId());
        Session session = session(command.sessionReference(), command.correlationId());
        if (!command.challengeReference().equals(session.challengeReference()) || blank(command.authorizationCode())) {
            ProviderError error = error("INVALID_AUTHORIZATION", "Codice OTP Aruba non valido", ErrorCategory.AUTHENTICATION, false);
            return new AuthenticationResult(false, error.message(), error, command.correlationId());
        }
        try {
            String providerSession = call("opensession", "<Identity>" + identity(command.authorizationCode()) + "</Identity>", command.timeout(), command.correlationId());
            if (blank(providerSession)) {
                ProviderError error = error("ARUBA_SESSION_REJECTED", "Aruba non ha aperto la sessione", ErrorCategory.AUTHENTICATION, false);
                return new AuthenticationResult(false, error.message(), error, command.correlationId());
            }
            sessions.put(command.sessionReference(), new Session(session.expiresAt(), session.challengeReference(), providerSession));
            return new AuthenticationResult(true, "Sessione Aruba autorizzata", null, command.correlationId());
        } catch (ProviderAdapterException exception) {
            return new AuthenticationResult(false, exception.error().message(), exception.error(), command.correlationId());
        }
    }

    @Override
    public SubmissionResult submit(SignatureCommand command) {
        requireTimeout(command.timeout(), command.correlationId());
        Session session = authenticated(command.sessionReference(), command.correlationId());
        if (command.payloadMode() != PayloadMode.DOCUMENT) return failed(command, "DIGEST_NOT_SUPPORTED",
                "Aruba ARSS pdfsignatureV2 richiede il PDF completo");
        if (command.payload() == null || command.payload().length == 0) throw exception("EMPTY_DOCUMENT",
                "Documento PDF richiesto", ErrorCategory.VALIDATION, false, command.correlationId());
        String operation = idempotency.computeIfAbsent(command.idempotencyKey(), ignored -> "aruba-operation-" + UUID.randomUUID());
        Job existing = jobs.get(operation);
        if (existing != null) return result(existing, command.correlationId());
        String request = "<SignRequestV2><binaryinput>" + Base64.getEncoder().encodeToString(command.payload())
                + "</binaryinput><certID>" + xml(properties.certificateId()) + "</certID><identity>"
                + identity(null) + "</identity><requiredmark>false</requiredmark><session_id>"
                + xml(session.providerSessionId()) + "</session_id><srcName>" + xml(command.reportIdentifier())
                + ".pdf</srcName><transport>BYNARYNET</transport></SignRequestV2>";
        try {
            String response = call("pdfsignatureV2", request, command.timeout(), command.correlationId());
            ArubaResponse aruba = parseSignResponse(response, command.correlationId());
            if (!success(aruba.status(), aruba.code()) || aruba.document() == null || aruba.document().length == 0) {
                ProviderError error = error(nonBlank(aruba.code(), "ARUBA_SIGNING_FAILED"),
                        nonBlank(aruba.description(), "Aruba ha rifiutato la firma"), ErrorCategory.PROVIDER, false);
                Job failed = new Job(operation, SubmissionState.FAILED, null, null, null, error);
                jobs.put(operation, failed);
                return result(failed, command.correlationId());
            }
            String reference = nonBlank(aruba.code(), "ARUBA-" + UUID.randomUUID());
            Job completed = new Job(operation, SubmissionState.SUCCEEDED, reference,
                    command.reportIdentifier() + "-signed.pdf", aruba.document(), null);
            jobs.put(operation, completed);
            return result(completed, command.correlationId());
        } catch (ProviderAdapterException exception) {
            Job failed = new Job(operation, SubmissionState.FAILED, null, null, null, exception.error());
            jobs.put(operation, failed);
            return result(failed, command.correlationId());
        }
    }

    @Override public PollResult poll(PollCommand command) {
        requireTimeout(command.timeout(), command.correlationId()); authenticated(command.sessionReference(), command.correlationId());
        Job job = job(command.operationReference(), command.correlationId());
        return new PollResult(job.operationReference(), job.state(), job.providerReference(), job.error(), command.correlationId());
    }

    @Override public RetrievedDocument retrieve(RetrieveCommand command) {
        requireTimeout(command.timeout(), command.correlationId()); authenticated(command.sessionReference(), command.correlationId());
        Job job = job(command.operationReference(), command.correlationId());
        if (job.state() != SubmissionState.SUCCEEDED) throw exception("RESULT_NOT_READY", "Documento firmato Aruba non disponibile", ErrorCategory.PROVIDER, false, command.correlationId());
        return new RetrievedDocument(job.filename(), "application/pdf", job.content(), job.providerReference(), command.correlationId());
    }

    private String call(String operation, String body, Duration timeout, String correlationId) {
        String envelope = "<soapenv:Envelope xmlns:soapenv=\"" + SOAP_NS + "\" xmlns:arub=\"" + ARUBA_NS + "\"><soapenv:Body><arub:"
                + operation + ">" + body + "</arub:" + operation + "></soapenv:Body></soapenv:Envelope>";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.serviceUrl())).timeout(timeout)
                    .header("Content-Type", "text/xml; charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(envelope)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500) throw exception("ARUBA_UNAVAILABLE", "Servizio Aruba temporaneamente non disponibile", ErrorCategory.TEMPORARY, true, correlationId);
            if (response.statusCode() >= 400) throw exception("ARUBA_REQUEST_REJECTED", "Richiesta Aruba rifiutata", ErrorCategory.PROVIDER, false, correlationId);
            return response.body();
        } catch (ProviderAdapterException exception) { throw exception;
        } catch (java.net.http.HttpTimeoutException exception) { throw exception("ARUBA_TIMEOUT", "Timeout del servizio Aruba", ErrorCategory.TIMEOUT, true, correlationId);
        } catch (Exception exception) { throw exception("ARUBA_UNAVAILABLE", "Servizio Aruba non raggiungibile", ErrorCategory.TEMPORARY, true, correlationId); }
    }

    private ArubaResponse parseSignResponse(String response, String correlationId) {
        try {
            Document doc = parseXml(response); String fault = text(doc, "faultstring");
            if (!blank(fault)) throw exception("ARUBA_SOAP_FAULT", fault, ErrorCategory.PROVIDER, false, correlationId);
            String encoded = nonBlank(text(doc, "binaryoutput"), text(doc, "stream"));
            return new ArubaResponse(text(doc, "status"), text(doc, "return_code"), text(doc, "description"),
                    blank(encoded) ? null : Base64.getDecoder().decode(encoded.replaceAll("\\s", "")));
        } catch (ProviderAdapterException exception) { throw exception;
        } catch (Exception exception) { throw exception("ARUBA_INVALID_RESPONSE", "Risposta Aruba non valida", ErrorCategory.PROVIDER, false, correlationId); }
    }

    private String identity(String otp) { return (otp == null ? "" : "<otpPwd>" + xml(otp) + "</otpPwd>")
            + "<typeOtpAuth>" + xml(properties.otpAuthenticationType()) + "</typeOtpAuth><user>" + xml(properties.username())
            + "</user><userPWD>" + xml(properties.password()) + "</userPWD>"; }
    private Document parseXml(String value) throws Exception { var factory = DocumentBuilderFactory.newInstance(); factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); factory.setNamespaceAware(true); return factory.newDocumentBuilder().parse(new InputSource(new StringReader(value))); }
    private String text(Document doc, String name) { var nodes = doc.getElementsByTagNameNS("*", name); return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent(); }
    private boolean success(String status, String code) { return "OK".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status) || "0".equals(code); }
    private SubmissionResult failed(SignatureCommand command, String code, String message) { ProviderError error = error(code, message, ErrorCategory.VALIDATION, false); String operation = idempotency.computeIfAbsent(command.idempotencyKey(), ignored -> "aruba-operation-" + UUID.randomUUID()); Job job = new Job(operation, SubmissionState.FAILED, null, null, null, error); jobs.putIfAbsent(operation, job); return result(jobs.get(operation), command.correlationId()); }
    private Session session(String reference, String correlationId) { Session value = sessions.get(reference); if (value == null) throw exception("SESSION_NOT_FOUND", "Sessione Aruba non trovata", ErrorCategory.NOT_FOUND, false, correlationId); if (!value.expiresAt().isAfter(OffsetDateTime.now())) throw exception("SESSION_TIMEOUT", "Sessione Aruba scaduta", ErrorCategory.TIMEOUT, true, correlationId); return value; }
    private Session authenticated(String reference, String correlationId) { Session value = session(reference, correlationId); if (blank(value.providerSessionId())) throw exception("SESSION_NOT_AUTHENTICATED", "Sessione Aruba non autenticata", ErrorCategory.AUTHENTICATION, false, correlationId); return value; }
    private Job job(String reference, String correlationId) { Job value = jobs.get(reference); if (value == null) throw exception("OPERATION_NOT_FOUND", "Operazione Aruba non trovata", ErrorCategory.NOT_FOUND, false, correlationId); return value; }
    private void requireConfigured(String correlationId) { if (blank(properties.username()) || blank(properties.password())) throw exception("ARUBA_CREDENTIALS_NOT_CONFIGURED", "Credenziali Aruba non configurate", ErrorCategory.AUTHENTICATION, false, correlationId); }
    private void requireTimeout(Duration timeout, String correlationId) { if (timeout == null || timeout.isNegative() || timeout.isZero()) throw exception("PROVIDER_TIMEOUT", "Timeout provider esaurito", ErrorCategory.TIMEOUT, true, correlationId); }
    private ProviderAdapterException exception(String code, String message, ErrorCategory category, boolean retryable, String correlationId) { return new ProviderAdapterException(error(code, message, category, retryable), correlationId); }
    private ProviderError error(String code, String message, ErrorCategory category, boolean retryable) { return new ProviderError(code, message, category, retryable); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String nonBlank(String first, String fallback) { return blank(first) ? fallback : first.trim(); }
    private static String xml(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }
    private record Session(OffsetDateTime expiresAt, String challengeReference, String providerSessionId) { }
    private record Job(String operationReference, SubmissionState state, String providerReference, String filename, byte[] content, ProviderError error) { }
    private record ArubaResponse(String status, String code, String description, byte[] document) { }
    private SubmissionResult result(Job job, String correlationId) { return new SubmissionResult(job.operationReference(), job.state(), job.providerReference(), job.error(), correlationId); }
}
