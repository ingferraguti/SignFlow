package it.signflow.signatures;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Provider-neutral remote signature lifecycle. Implementations may call a remote service or a strictly local
 * test provider, but provider-specific DTOs, names and credentials must not leak through this contract.
 */
public interface SignatureProviderAdapter {
    String adapterType();

    SessionResult openSession(OpenSessionCommand command);

    ChallengeResult requestChallenge(ChallengeCommand command);

    AuthenticationResult authenticate(AuthenticationCommand command);

    SubmissionResult submit(SignatureCommand command);

    PollResult poll(PollCommand command);

    RetrievedDocument retrieve(RetrieveCommand command);

    record OpenSessionCommand(String providerCode, String accountAlias, String correlationId, Duration timeout) {
    }

    record SessionResult(String sessionReference, OffsetDateTime expiresAt, String correlationId) {
    }

    record ChallengeCommand(String sessionReference, String correlationId, Duration timeout) {
    }

    record ChallengeResult(String challengeReference, String authenticationMode, OffsetDateTime expiresAt,
                           String correlationId) {
    }

    record AuthenticationCommand(String sessionReference, String challengeReference, String authorizationCode,
                                 String correlationId, Duration timeout) {
    }

    record AuthenticationResult(boolean authenticated, String message, ProviderError error, String correlationId) {
    }

    record SignatureCommand(String sessionReference, String reportIdentifier, PayloadMode payloadMode,
                            byte[] payload, String digestAlgorithm, int retryCount, int failuresBeforeSuccess,
                            String idempotencyKey, RetryPolicy retryPolicy, String correlationId, Duration timeout) {
    }

    record SubmissionResult(String operationReference, SubmissionState state, String providerReference,
                            ProviderError error, String correlationId) {
    }

    record PollCommand(String sessionReference, String operationReference, String correlationId, Duration timeout) {
    }

    record PollResult(String operationReference, SubmissionState state, String providerReference,
                      ProviderError error, String correlationId) {
    }

    record RetrieveCommand(String sessionReference, String operationReference, String correlationId, Duration timeout) {
    }

    record RetrievedDocument(String filename, String mediaType, byte[] content, String providerReference,
                             String correlationId) {
    }

    record RetryPolicy(int maxAttempts, Duration initialBackoff) {
    }

    record ProviderError(String code, String message, ErrorCategory category, boolean retryable) {
    }

    enum PayloadMode { DOCUMENT, DIGEST }

    enum SubmissionState { ACCEPTED, PROCESSING, SUCCEEDED, FAILED }

    enum ErrorCategory { AUTHENTICATION, VALIDATION, TEMPORARY, TIMEOUT, PROVIDER, NOT_FOUND }

    final class ProviderAdapterException extends RuntimeException {
        private final ProviderError error;
        private final String correlationId;

        public ProviderAdapterException(ProviderError error, String correlationId) {
            super(error.message());
            this.error = error;
            this.correlationId = correlationId;
        }

        public ProviderError error() { return error; }

        public String correlationId() { return correlationId; }
    }
}
