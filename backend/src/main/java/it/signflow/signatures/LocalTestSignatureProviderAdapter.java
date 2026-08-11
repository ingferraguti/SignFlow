package it.signflow.signatures;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process adapter used only by automated/local tests. It creates a real cryptographic PAdES with explicitly
 * supplied test key material; it is deliberately not registered as a production Spring component.
 */
public final class LocalTestSignatureProviderAdapter implements SignatureProviderAdapter {
    public static final String TEST_AUTHORIZATION_CODE = "111111";
    private final DigitalSignatureEngine engine;
    private final byte[] testPkcs12;
    private final char[] password;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, String> idempotency = new ConcurrentHashMap<>();

    public LocalTestSignatureProviderAdapter(DigitalSignatureEngine engine, byte[] testPkcs12, char[] password) {
        this.engine = engine;
        this.testPkcs12 = testPkcs12.clone();
        this.password = password.clone();
    }

    @Override
    public String adapterType() {
        return "LOCAL_TEST_PADES";
    }

    @Override
    public SessionResult openSession(OpenSessionCommand command) {
        requireTimeout(command.timeout(), command.correlationId());
        String reference = "local-test-session-" + UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);
        sessions.put(reference, new Session(expiresAt, false, null));
        return new SessionResult(reference, expiresAt, command.correlationId());
    }

    @Override
    public ChallengeResult requestChallenge(ChallengeCommand command) {
        requireTimeout(command.timeout(), command.correlationId());
        Session session = session(command.sessionReference(), command.correlationId());
        String challenge = "local-test-challenge-" + UUID.randomUUID();
        sessions.put(command.sessionReference(), new Session(session.expiresAt(), false, challenge));
        return new ChallengeResult(challenge, "TEST_OTP", OffsetDateTime.now().plusMinutes(2), command.correlationId());
    }

    @Override
    public AuthenticationResult authenticate(AuthenticationCommand command) {
        requireTimeout(command.timeout(), command.correlationId());
        Session session = session(command.sessionReference(), command.correlationId());
        if (!command.challengeReference().equals(session.challengeReference())) {
            ProviderError error = error("INVALID_CHALLENGE", "Challenge locale di test non valido",
                    ErrorCategory.AUTHENTICATION, false);
            return new AuthenticationResult(false, error.message(), error, command.correlationId());
        }
        boolean valid = TEST_AUTHORIZATION_CODE.equals(command.authorizationCode());
        ProviderError error = valid ? null : error("INVALID_AUTHORIZATION", "OTP locale di test non valido",
                ErrorCategory.AUTHENTICATION, false);
        if (valid) sessions.put(command.sessionReference(), new Session(session.expiresAt(), true, session.challengeReference()));
        return new AuthenticationResult(valid, valid ? "Sessione locale di test autorizzata" : error.message(),
                error, command.correlationId());
    }

    @Override
    public SubmissionResult submit(SignatureCommand command) {
        requireTimeout(command.timeout(), command.correlationId());
        authenticated(command.sessionReference(), command.correlationId());
        String operation = idempotency.computeIfAbsent(command.idempotencyKey(), ignored -> "local-test-operation-" + UUID.randomUUID());
        Job existing = jobs.get(operation);
        if (existing != null) return submission(existing, command.correlationId());
        if (command.payloadMode() != PayloadMode.DOCUMENT) {
            ProviderError error = error("DIGEST_ONLY_NOT_SUPPORTED_LOCALLY",
                    "Il provider locale PAdES richiede il PDF; il contratto remoto supporta anche il digest",
                    ErrorCategory.VALIDATION, false);
            Job failed = new Job(operation, SubmissionState.FAILED, null, null, null, error);
            jobs.put(operation, failed);
            return submission(failed, command.correlationId());
        }
        try {
            DigitalSignatureEngine.PadesResult result = engine.createTestPades(new DigitalSignatureEngine.PadesRequest(
                    command.payload(), testPkcs12, password, command.reportIdentifier() + ".pdf",
                    "Firma PAdES locale di test - nessun valore legale", "Ambiente test SignFlow",
                    command.correlationId()));
            String providerReference = "LOCAL-TEST-PADES-" + UUID.randomUUID();
            Job succeeded = new Job(operation, SubmissionState.SUCCEEDED, providerReference,
                    command.reportIdentifier() + "-TEST-PAdES.pdf", result.signedPdf(), null);
            jobs.put(operation, succeeded);
            return submission(succeeded, command.correlationId());
        } catch (RuntimeException exception) {
            ProviderError error = error("LOCAL_TEST_SIGNING_FAILED", sanitized(exception.getMessage()),
                    ErrorCategory.VALIDATION, false);
            Job failed = new Job(operation, SubmissionState.FAILED, null, null, null, error);
            jobs.put(operation, failed);
            return submission(failed, command.correlationId());
        }
    }

    @Override
    public PollResult poll(PollCommand command) {
        requireTimeout(command.timeout(), command.correlationId());
        authenticated(command.sessionReference(), command.correlationId());
        Job job = job(command.operationReference(), command.correlationId());
        return new PollResult(job.operationReference(), job.state(), job.providerReference(), job.error(),
                command.correlationId());
    }

    @Override
    public RetrievedDocument retrieve(RetrieveCommand command) {
        requireTimeout(command.timeout(), command.correlationId());
        authenticated(command.sessionReference(), command.correlationId());
        Job job = job(command.operationReference(), command.correlationId());
        if (job.state() != SubmissionState.SUCCEEDED) throw exception("RESULT_NOT_READY",
                "Documento PAdES locale non disponibile", ErrorCategory.PROVIDER, false, command.correlationId());
        return new RetrievedDocument(job.filename(), "application/pdf", job.content(), job.providerReference(),
                command.correlationId());
    }

    private SubmissionResult submission(Job job, String correlationId) {
        return new SubmissionResult(job.operationReference(), job.state(), job.providerReference(), job.error(), correlationId);
    }

    private Session session(String reference, String correlationId) {
        Session session = sessions.get(reference);
        if (session == null) throw exception("SESSION_NOT_FOUND", "Sessione locale di test non trovata",
                ErrorCategory.NOT_FOUND, false, correlationId);
        if (!session.expiresAt().isAfter(OffsetDateTime.now())) throw exception("SESSION_TIMEOUT",
                "Sessione locale di test scaduta", ErrorCategory.TIMEOUT, true, correlationId);
        return session;
    }

    private void authenticated(String reference, String correlationId) {
        if (!session(reference, correlationId).authenticated()) throw exception("SESSION_NOT_AUTHENTICATED",
                "Sessione locale di test non autenticata", ErrorCategory.AUTHENTICATION, false, correlationId);
    }

    private Job job(String reference, String correlationId) {
        Job job = jobs.get(reference);
        if (job == null) throw exception("OPERATION_NOT_FOUND", "Operazione locale di test non trovata",
                ErrorCategory.NOT_FOUND, false, correlationId);
        return job;
    }

    private void requireTimeout(Duration timeout, String correlationId) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) throw exception("PROVIDER_TIMEOUT",
                "Timeout provider esaurito", ErrorCategory.TIMEOUT, true, correlationId);
    }

    private ProviderAdapterException exception(String code, String message, ErrorCategory category,
                                               boolean retryable, String correlationId) {
        return new ProviderAdapterException(error(code, message, category, retryable), correlationId);
    }

    private ProviderError error(String code, String message, ErrorCategory category, boolean retryable) {
        return new ProviderError(code, message, category, retryable);
    }

    private String sanitized(String message) {
        return message == null || message.isBlank() ? "Errore locale di test" : message;
    }

    private record Session(OffsetDateTime expiresAt, boolean authenticated, String challengeReference) {
    }

    private record Job(String operationReference, SubmissionState state, String providerReference, String filename,
                       byte[] content, ProviderError error) {
    }
}
