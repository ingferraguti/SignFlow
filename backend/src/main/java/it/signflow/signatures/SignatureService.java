package it.signflow.signatures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.signflow.reports.ReportState;
import it.signflow.reports.ReportWorkflowOperation;
import it.signflow.reports.ReportWorkflowService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SignatureService {
    private final SignatureRepository repository;
    private final ReportWorkflowService workflow;
    private final ObjectMapper objectMapper;
    private final Map<String, SignatureProviderAdapter> adapters;

    public SignatureService(SignatureRepository repository, ReportWorkflowService workflow,
                            ObjectMapper objectMapper, List<SignatureProviderAdapter> adapters) {
        this.repository = repository;
        this.workflow = workflow;
        this.objectMapper = objectMapper;
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(
                SignatureProviderAdapter::adapterType, Function.identity()));
    }

    @Transactional
    public ProviderSessionResponse openSession(String username, ProviderSessionRequest request) {
        SignatureRepository.SignerAccount account = account(username);
        SignatureProviderAdapter adapter = adapter(account.adapterType());
        var result = adapter.authenticate(new SignatureProviderAdapter.AuthenticationCommand(
                account.providerCode(), account.accountAlias(), request.authorizationCode()));
        if (!result.authenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, result.message());
        return repository.insertSession(UUID.randomUUID(), account, username, OffsetDateTime.now().plusMinutes(5));
    }

    @Transactional
    public SignatureBatchResponse create(String username, CreateSignatureBatchRequest request) {
        String key = required(request.operationKey(), "Operation key is required");
        SignatureSelectionMode mode = request.selectionMode();
        List<UUID> ids = request.reportIds() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.reportIds()));
        if (mode != SignatureSelectionMode.FILTERED && ids.isEmpty()) {
            throw unprocessable("Select at least one report");
        }
        if (mode == SignatureSelectionMode.SINGLE && ids.size() != 1) {
            throw unprocessable("Single signature requires exactly one report");
        }
        String filterSnapshot = mode == SignatureSelectionMode.FILTERED ? json(request.filters()) : null;
        String fingerprint = hash(mode + "|" + ids.stream().sorted().toList() + "|" + filterSnapshot);
        var repeated = repository.findCreate(username, key);
        if (repeated.isPresent()) {
            if (!repeated.get().fingerprint().equals(fingerprint)) throw conflict("Idempotency key already used");
            return detail(username, repeated.get().batchId());
        }
        SignatureRepository.SignerAccount account = account(username);
        List<SignatureRepository.EligibleReport> reports = repository.eligible(username, mode, ids, request.filters());
        if (mode != SignatureSelectionMode.FILTERED && reports.size() != ids.size()) {
            throw unprocessable("Every selected report must be visible, approved and have an active document");
        }
        if (reports.isEmpty()) throw unprocessable("No approved report matches the selection");
        UUID id = UUID.randomUUID();
        repository.insertBatch(id, username, account, mode, filterSnapshot, key, fingerprint, reports);
        return detail(username, id);
    }

    @Transactional
    public SignatureBatchResponse signSingle(String username, SingleSignatureRequest request) {
        SignatureBatchResponse batch = create(username, new CreateSignatureBatchRequest(
                SignatureSelectionMode.SINGLE, List.of(request.reportId()), null, request.operationKey()));
        if (batch.state() == SignatureBatchState.DRAFT) {
            batch = confirm(username, batch.id(), new SignatureOperationRequest(
                    request.operationKey() + "-confirm", request.providerSessionId()));
        }
        if (batch.state() == SignatureBatchState.CONFIRMED) {
            batch = start(username, batch.id(), new SignatureOperationRequest(
                    request.operationKey() + "-start", request.providerSessionId()));
        }
        return batch;
    }

    @Transactional
    public SignatureBatchResponse confirm(String username, UUID batchId, SignatureOperationRequest request) {
        repository.lock(batchId);
        SignatureRepository.BatchData batch = owned(username, batchId);
        String fingerprint = hash("CONFIRM|" + request.providerSessionId());
        if (repeated(batchId, request.operationKey(), fingerprint)) return detail(username, batchId);
        if (batch.state() != SignatureBatchState.DRAFT) throw conflict("Only a draft batch can be confirmed");
        SignatureRepository.SessionData session = activeSession(username, request.providerSessionId());
        if (!session.accountId().equals(account(username).id())) throw conflict("Provider session uses another account");
        for (SignatureRepository.AttemptData attempt : repository.attempts(batchId)) {
            if (!"APPROVED".equals(attempt.reportState())) {
                throw conflict("Report " + attempt.reportIdentifier() + " is no longer approved");
            }
            workflow.transitionForSignature(attempt.reportId(), attempt.workflowVersion(),
                    batchId + "-confirm-" + attempt.id(), ReportWorkflowOperation.CREATE_SIGNATURE_BATCH,
                    ReportState.SIGN_BATCH_CREATED, username, "Batch mock " + batchId);
        }
        repository.confirm(batchId, session.id());
        repository.insertOperation(batchId, null, request.operationKey(), "CONFIRM", fingerprint);
        return detail(username, batchId);
    }

    @Transactional
    public SignatureBatchResponse start(String username, UUID batchId, SignatureOperationRequest request) {
        repository.lock(batchId);
        SignatureRepository.BatchData batch = owned(username, batchId);
        String fingerprint = hash("START|" + request.providerSessionId());
        if (repeated(batchId, request.operationKey(), fingerprint)) return detail(username, batchId);
        if (batch.state() != SignatureBatchState.CONFIRMED) throw conflict("Only a confirmed batch can be started");
        SignatureRepository.SessionData session = activeSession(username, request.providerSessionId());
        repository.running(batchId);
        for (SignatureRepository.AttemptData attempt : repository.attempts(batchId)) process(username, batchId, attempt, session, false);
        repository.finalizeBatch(batchId);
        repository.insertOperation(batchId, null, request.operationKey(), "START", fingerprint);
        return detail(username, batchId);
    }

    @Transactional
    public SignatureBatchResponse retry(String username, UUID batchId, UUID attemptId,
                                        SignatureOperationRequest request) {
        repository.lock(batchId);
        SignatureRepository.BatchData batch = owned(username, batchId);
        String fingerprint = hash("RETRY|" + attemptId + "|" + request.providerSessionId());
        if (repeated(batchId, request.operationKey(), fingerprint)) return detail(username, batchId);
        if (batch.state() != SignatureBatchState.FAILED && batch.state() != SignatureBatchState.PARTIAL_SUCCESS) {
            throw conflict("Retry is allowed only after a failed or partially successful batch");
        }
        SignatureRepository.AttemptData attempt = repository.attempt(batchId, attemptId)
                .orElseThrow(() -> notFound("Signature attempt not found"));
        if (attempt.state() != SignatureAttemptState.FAILED || attempt.retryCount() >= attempt.maxRetries()) {
            throw conflict("The selected attempt cannot be retried");
        }
        process(username, batchId, attempt, activeSession(username, request.providerSessionId()), true);
        repository.finalizeBatch(batchId);
        repository.insertOperation(batchId, attemptId, request.operationKey(), "RETRY", fingerprint);
        return detail(username, batchId);
    }

    @Transactional
    public SignatureBatchResponse cancel(String username, UUID batchId, SignatureOperationRequest request) {
        repository.lock(batchId);
        SignatureRepository.BatchData batch = owned(username, batchId);
        String fingerprint = hash("CANCEL");
        if (repeated(batchId, request.operationKey(), fingerprint)) return detail(username, batchId);
        if (batch.state() != SignatureBatchState.DRAFT && batch.state() != SignatureBatchState.CONFIRMED) {
            throw conflict("A batch can be cancelled only before it starts");
        }
        if (batch.state() == SignatureBatchState.CONFIRMED) {
            for (SignatureRepository.AttemptData attempt : repository.attempts(batchId)) {
                workflow.transitionForSignature(attempt.reportId(), attempt.workflowVersion(),
                        batchId + "-cancel-" + attempt.id(), ReportWorkflowOperation.CANCEL_SIGNATURE_BATCH,
                        ReportState.APPROVED, username, "Annullamento batch mock " + batchId);
            }
        }
        repository.cancel(batchId);
        repository.insertOperation(batchId, null, request.operationKey(), "CANCEL", fingerprint);
        return detail(username, batchId);
    }

    public SignatureBatchResponse detail(String username, UUID batchId) {
        SignatureRepository.BatchData batch = owned(username, batchId);
        List<SignatureAttemptResponse> attempts = repository.attempts(batchId).stream().map(this::response).toList();
        return new SignatureBatchResponse(batch.id(), batch.signerUsername(), batch.providerCode(),
                batch.selectionMode(), batch.filterSnapshot(), batch.state(), batch.version(), batch.totalCount(),
                batch.successCount(), batch.failureCount(), batch.createdAt(), batch.confirmedAt(), batch.startedAt(),
                batch.completedAt(), batch.cancelledAt(), MockSignatureProvider.MOCK_NOTICE, attempts);
    }

    public List<SignatureBatchResponse> list(String username) {
        account(username);
        return repository.batches(username).stream().map(batch -> detail(username, batch.id())).toList();
    }

    public SignatureArtifact artifact(String username, UUID artifactId) {
        account(username);
        return repository.artifact(artifactId, username).orElseThrow(() -> notFound("Mock artifact not found"));
    }

    private void process(String username, UUID batchId, SignatureRepository.AttemptData initial,
                         SignatureRepository.SessionData session, boolean retry) {
        SignatureRepository.AttemptData attempt = repository.attempt(batchId, initial.id()).orElseThrow();
        ReportWorkflowOperation startOperation = retry ? ReportWorkflowOperation.RETRY_MOCK_SIGNATURE
                : ReportWorkflowOperation.START_SIGNATURE;
        workflow.transitionForSignature(attempt.reportId(), attempt.workflowVersion(),
                batchId + (retry ? "-retry-start-" : "-start-") + attempt.id() + "-" + attempt.retryCount(),
                startOperation, ReportState.SIGNING, username, "Provider " + session.providerCode());
        repository.markSigning(attempt.id(), retry);
        SignatureRepository.AttemptData signing = repository.attempt(batchId, attempt.id()).orElseThrow();
        var result = adapter(session.adapterType()).sign(new SignatureProviderAdapter.SignatureCommand(
                session.providerCode(), session.accountAlias(), signing.reportIdentifier(),
                signing.retryCount(), signing.failuresBeforeSuccess()));
        if (result.successful()) {
            repository.succeed(signing.id(), result);
            workflow.transitionForSignature(signing.reportId(), signing.workflowVersion(),
                    batchId + "-success-" + signing.id() + "-" + signing.retryCount(),
                    ReportWorkflowOperation.COMPLETE_MOCK_SIGNATURE, ReportState.SIGNED, username,
                    result.providerReference());
        } else {
            repository.fail(signing.id(), result);
            workflow.transitionForSignature(signing.reportId(), signing.workflowVersion(),
                    batchId + "-failure-" + signing.id() + "-" + signing.retryCount(),
                    ReportWorkflowOperation.FAIL_MOCK_SIGNATURE, ReportState.SIGN_ERROR, username,
                    result.errorCode());
        }
    }

    private boolean repeated(UUID batchId, String key, String fingerprint) {
        String normalized = required(key, "Operation key is required");
        var stored = repository.operation(batchId, normalized);
        if (stored.isEmpty()) return false;
        if (!stored.get().fingerprint().equals(fingerprint)) throw conflict("Idempotency key already used");
        return true;
    }

    private SignatureRepository.SignerAccount account(String username) {
        if (username == null || username.isBlank()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return repository.account(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Active mock signature account not available"));
    }

    private SignatureRepository.SessionData activeSession(String username, UUID id) {
        if (id == null) throw unprocessable("Provider session is required");
        SignatureRepository.SessionData session = repository.session(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Provider session not found"));
        if (!"ACTIVE".equals(session.state()) || !session.expiresAt().isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Provider session expired");
        }
        return session;
    }

    private SignatureRepository.BatchData owned(String username, UUID id) {
        return repository.batch(id, username).orElseThrow(() -> notFound("Signature batch not found"));
    }

    private SignatureProviderAdapter adapter(String adapterType) {
        SignatureProviderAdapter value = adapters.get(adapterType);
        if (value == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Signature provider adapter not available");
        return value;
    }

    private SignatureAttemptResponse response(SignatureRepository.AttemptData a) {
        return new SignatureAttemptResponse(a.id(), a.reportId(), a.reportIdentifier(), a.state(),
                a.retryCount(), a.maxRetries(), a.providerReference(), a.artifactId(), a.artifactName(),
                a.artifactNotice(), a.errorCode(), a.errorMessage(), a.startedAt(), a.completedAt());
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid filters", exception); }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException unprocessable(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
