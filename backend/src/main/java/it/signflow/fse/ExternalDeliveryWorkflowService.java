package it.signflow.fse;

import static it.signflow.fse.ExternalDeliveryModels.*;

import it.signflow.reports.MinioObjectStorage;
import it.signflow.reports.ReportState;
import it.signflow.reports.ReportWorkflowOperation;
import it.signflow.reports.ReportWorkflowService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExternalDeliveryWorkflowService {
    private final ExternalDeliveryRepository repository;
    private final ReportWorkflowService reportWorkflow;
    private final MinioObjectStorage storage;
    private final DocumentPreflightValidator validator;
    private final FseGatewayAdapter fseAdapter;
    private final ConservationAdapter conservationAdapter;
    private final int maxRetries;

    public ExternalDeliveryWorkflowService(ExternalDeliveryRepository repository,
            ReportWorkflowService reportWorkflow, MinioObjectStorage storage,
            DocumentPreflightValidator validator, FseGatewayAdapter fseAdapter,
            ConservationAdapter conservationAdapter,
            @Value("${signflow.external-delivery.max-retries:2}") int maxRetries) {
        this.repository = repository; this.reportWorkflow = reportWorkflow; this.storage = storage;
        this.validator = validator; this.fseAdapter = fseAdapter; this.conservationAdapter = conservationAdapter;
        this.maxRetries = Math.max(0, Math.min(10, maxRetries));
    }

    public OperationPage search(String channel, String state, String correlationId,
                                String reportIdentifier, int page, int size) {
        return repository.search(channel, state, correlationId, reportIdentifier, page, size);
    }

    public OperationDetail detail(UUID id) {
        ExternalDeliveryRepository.StoredOperation stored = operation(id, false);
        State state = stored.summary().state();
        boolean retry = Set.of(State.FSE_VALIDATION_ERROR, State.FSE_REJECTED,
                State.CONSERVATION_REJECTED, State.TIMEOUT).contains(state)
                && stored.summary().attemptCount() < stored.summary().maxRetries();
        boolean reconcile = Set.of(State.FSE_SENT, State.CONSERVATION_SENT, State.TIMEOUT).contains(state);
        return new OperationDetail(stored.summary(), stored.metadata(), repository.attempts(id),
                repository.receipts(id), retry, reconcile);
    }

    @Transactional
    public OperationDetail sendFse(UUID reportId, CommandRequest request, String actor) {
        return start(reportId, Channel.FSE, request, actor);
    }

    @Transactional
    public OperationDetail sendConservation(UUID reportId, CommandRequest request, String actor) {
        return start(reportId, Channel.CONSERVATION, request, actor);
    }

    @Transactional
    public OperationDetail retry(UUID operationId, CommandRequest request, String actor) {
        ExternalDeliveryRepository.StoredOperation stored = operation(operationId, true);
        String key = key(request.operationKey());
        String fingerprint = fingerprint("RETRY", operationId, request.expectedVersion());
        OperationDetail repeated = repeated(stored, key, fingerprint);
        if (repeated != null) return repeated;
        OperationSummary summary = stored.summary();
        if (!Set.of(State.FSE_VALIDATION_ERROR, State.FSE_REJECTED,
                State.CONSERVATION_REJECTED, State.TIMEOUT).contains(summary.state())) {
            throw conflict("Retry non consentito dallo stato " + summary.state());
        }
        if (summary.attemptCount() >= summary.maxRetries()) throw conflict("Numero massimo di retry raggiunto");
        requireReportVersion(summary, request.expectedVersion());
        byte[] document = document(summary.documentId());
        DocumentPreflightValidator.ValidationResult validation = validator.validate(document,
                repository.preparation(summary.reportId()).orElseThrow().signatureKind());
        if (!validation.valid()) {
            if (summary.channel() != Channel.FSE) throw unprocessable(validation.detail());
            updateOrConflict(summary, State.FSE_VALIDATION_ERROR, null, true, validation.code(), validation.detail());
            transition(stored, request, ReportWorkflowOperation.RETRY_FSE, ReportState.FSE_VALIDATION_ERROR, actor);
            repository.insertAttempt(UUID.randomUUID(), operationId, repository.eventCount(operationId) + 1,
                    "RETRY", "FAILURE", summary.correlationId(), summary.remoteReference(),
                    validation.code(), validation.detail());
            repository.insertCommand(operationId, key, "RETRY", fingerprint, State.FSE_VALIDATION_ERROR);
            return detail(operationId);
        }
        DeliveryRequest adapterRequest = new DeliveryRequest(summary.reportId().toString(), document,
                stored.metadata(), summary.correlationId(), summary.attemptCount() + 1);
        AdapterResult result = summary.channel() == Channel.FSE
                ? fseAdapter.submit(adapterRequest) : conservationAdapter.submit(adapterRequest);
        State target = sentState(summary.channel());
        updateOrConflict(summary, target, result.remoteReference(), true, result.errorCode(), result.errorMessage());
        transition(stored, request, summary.channel() == Channel.FSE ? ReportWorkflowOperation.RETRY_FSE
                : ReportWorkflowOperation.RETRY_CONSERVATION, reportState(target), actor);
        UUID attemptId = UUID.randomUUID();
        repository.insertAttempt(attemptId, operationId, repository.eventCount(operationId) + 1,
                "RETRY", outcome(result.status()), summary.correlationId(), result.remoteReference(),
                result.errorCode(), result.errorMessage());
        storeReceipt(operationId, attemptId, result);
        repository.insertCommand(operationId, key, "RETRY", fingerprint, target);
        return detail(operationId);
    }

    @Transactional
    public OperationDetail reconcile(UUID operationId, CommandRequest request, String actor) {
        ExternalDeliveryRepository.StoredOperation stored = operation(operationId, true);
        String key = key(request.operationKey());
        String fingerprint = fingerprint("RECONCILE", operationId, request.expectedVersion());
        OperationDetail repeated = repeated(stored, key, fingerprint);
        if (repeated != null) return repeated;
        OperationSummary summary = stored.summary();
        if (!Set.of(State.FSE_SENT, State.CONSERVATION_SENT, State.TIMEOUT).contains(summary.state())) {
            throw conflict("Riconciliazione non consentita dallo stato " + summary.state());
        }
        requireReportVersion(summary, request.expectedVersion());
        ReconciliationRequest adapterRequest = new ReconciliationRequest(summary.reportId().toString(),
                summary.remoteReference(), summary.correlationId(), summary.attemptCount(),
                repository.reconciliationCount(operationId) + 1);
        AdapterResult result = summary.channel() == Channel.FSE
                ? fseAdapter.reconcile(adapterRequest) : conservationAdapter.reconcile(adapterRequest);
        State target = resultState(summary.channel(), result.status());
        updateOrConflict(summary, target, result.remoteReference(), false, result.errorCode(), result.errorMessage());
        transition(stored, request, summary.channel() == Channel.FSE ? ReportWorkflowOperation.RECONCILE_FSE
                : ReportWorkflowOperation.RECONCILE_CONSERVATION, reportState(target, summary.channel()), actor);
        UUID attemptId = UUID.randomUUID();
        repository.insertAttempt(attemptId, operationId, repository.eventCount(operationId) + 1,
                "RECONCILE", outcome(result.status()), summary.correlationId(), result.remoteReference(),
                result.errorCode(), result.errorMessage());
        storeReceipt(operationId, attemptId, result);
        repository.insertCommand(operationId, key, "RECONCILE", fingerprint, target);
        return detail(operationId);
    }

    public ReceiptContent receipt(UUID id) {
        ExternalDeliveryRepository.StoredReceipt receipt = repository.receipt(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ricevuta non trovata"));
        return new ReceiptContent(receipt.type().toLowerCase() + "-mock.json", receipt.mimeType(), storage.get(receipt.objectKey()));
    }

    private OperationDetail start(UUID reportId, Channel channel, CommandRequest request, String actor) {
        String key = key(request.operationKey());
        ExternalDeliveryRepository.StoredOperation existing = repository.findByReportAndChannel(reportId, channel).orElse(null);
        if (existing != null) {
            OperationDetail repeated = repeated(existing, key, fingerprint("SEND", reportId, request.expectedVersion()));
            if (repeated != null) return repeated;
            throw conflict("Esiste gia una operazione " + channel + " per questo referto");
        }
        ExternalDeliveryRepository.Preparation preparation = repository.preparation(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Referto non trovato"));
        requireState(preparation, channel);
        if (preparation.workflowVersion() != request.expectedVersion()) {
            throw conflict("Il referto e stato aggiornato; versione corrente " + preparation.workflowVersion());
        }
        UUID operationId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String fingerprint = fingerprint("SEND", reportId, request.expectedVersion());
        String adapterCode = channel == Channel.FSE ? fseAdapter.code() : conservationAdapter.code();
        String validationError = validationError(preparation);
        byte[] document = null;
        DocumentPreflightValidator.ValidationResult validation = null;
        if (validationError == null) {
            document = storage.get(preparation.objectKey());
            validation = validator.validate(document, preparation.signatureKind());
            if (!validation.valid()) validationError = validation.detail();
        }
        if (validationError != null) {
            if (channel != Channel.FSE) throw unprocessable(validationError);
            String code = validation == null ? "FSE_METADATA_INVALID" : validation.code();
            repository.insert(operationId, preparation, channel, State.FSE_VALIDATION_ERROR, correlationId,
                    adapterCode, actor(actor), maxRetries, code, validationError);
            reportWorkflow.transitionForExternalDelivery(reportId, request.expectedVersion(), workflowKey(operationId, key),
                    ReportWorkflowOperation.VALIDATE_FSE, ReportState.FSE_VALIDATION_ERROR, actor(actor), correlationId);
            repository.insertAttempt(UUID.randomUUID(), operationId, 1, "VALIDATE", "FAILURE", correlationId,
                    null, code, validationError);
            repository.insertCommand(operationId, key, "SEND", fingerprint, State.FSE_VALIDATION_ERROR);
            return detail(operationId);
        }
        DeliveryMetadata metadata = metadata(preparation);
        DeliveryRequest adapterRequest = new DeliveryRequest(reportId.toString(), document, metadata, correlationId, 1);
        if (channel == Channel.FSE) {
            AdapterResult gatewayValidation = fseAdapter.validate(adapterRequest);
            if (gatewayValidation.status() != AdapterResult.Status.VALID) {
                String detail = gatewayValidation.errorMessage() == null ? "Validazione gateway FSE non superata" : gatewayValidation.errorMessage();
                repository.insert(operationId, preparation, channel, State.FSE_VALIDATION_ERROR, correlationId,
                        adapterCode, actor(actor), maxRetries, gatewayValidation.errorCode(), detail);
                reportWorkflow.transitionForExternalDelivery(reportId, request.expectedVersion(), workflowKey(operationId, key),
                        ReportWorkflowOperation.VALIDATE_FSE, ReportState.FSE_VALIDATION_ERROR, actor(actor), correlationId);
                repository.insertAttempt(UUID.randomUUID(), operationId, 1, "VALIDATE", "FAILURE", correlationId,
                        null, gatewayValidation.errorCode(), detail);
                repository.insertCommand(operationId, key, "SEND", fingerprint, State.FSE_VALIDATION_ERROR);
                return detail(operationId);
            }
        }
        AdapterResult result = channel == Channel.FSE ? fseAdapter.submit(adapterRequest) : conservationAdapter.submit(adapterRequest);
        State target = sentState(channel);
        ExternalDeliveryRepository.StoredOperation inserted = repository.insert(operationId, preparation, channel, target,
                correlationId, adapterCode, actor(actor), maxRetries, result.errorCode(), result.errorMessage());
        updateOrConflict(inserted.summary(), target, result.remoteReference(), true, result.errorCode(), result.errorMessage());
        reportWorkflow.transitionForExternalDelivery(reportId, request.expectedVersion(), workflowKey(operationId, key),
                channel == Channel.FSE ? ReportWorkflowOperation.SEND_FSE : ReportWorkflowOperation.SEND_CONSERVATION,
                reportState(target), actor(actor), correlationId);
        UUID attemptId = UUID.randomUUID();
        repository.insertAttempt(attemptId, operationId, 1, "SEND", outcome(result.status()), correlationId,
                result.remoteReference(), result.errorCode(), result.errorMessage());
        storeReceipt(operationId, attemptId, result);
        repository.insertCommand(operationId, key, "SEND", fingerprint, target);
        return detail(operationId);
    }

    private void transition(ExternalDeliveryRepository.StoredOperation stored, CommandRequest request,
                            ReportWorkflowOperation operation, ReportState target, String actor) {
        reportWorkflow.transitionForExternalDelivery(stored.summary().reportId(), request.expectedVersion(),
                workflowKey(stored.summary().id(), request.operationKey()), operation, target,
                actor(actor), stored.summary().correlationId());
    }

    private void updateOrConflict(OperationSummary summary, State target, String reference,
                                  boolean incrementAttempt, String errorCode, String errorMessage) {
        if (!repository.update(summary.id(), summary.version(), target, reference, incrementAttempt, errorCode, errorMessage)) {
            throw conflict("L'operazione e stata aggiornata da un altro processo");
        }
    }

    private OperationDetail repeated(ExternalDeliveryRepository.StoredOperation stored, String key, String fingerprint) {
        return repository.command(stored.summary().id(), key).map(command -> {
            if (!command.fingerprint().equals(fingerprint)) throw conflict("Chiave idempotente gia usata con una richiesta diversa");
            return detail(stored.summary().id());
        }).orElse(null);
    }

    private void storeReceipt(UUID operationId, UUID attemptId, AdapterResult result) {
        if (result.receipt() == null || result.receipt().length == 0) return;
        UUID receiptId = UUID.randomUUID();
        String key = "external-delivery/" + operationId + "/" + receiptId + ".json";
        storage.put(key, new ByteArrayInputStream(result.receipt()), result.receipt().length,
                result.mimeType() == null ? "application/json" : result.mimeType());
        repository.insertReceipt(receiptId, operationId, attemptId,
                result.receiptType() == null ? "SUBMISSION" : result.receiptType(), key,
                sha256(result.receipt()), result.mimeType() == null ? "application/json" : result.mimeType(),
                result.receipt().length);
    }

    private byte[] document(UUID documentId) {
        String objectKey = repository.documentObjectKey(documentId)
                .orElseThrow(() -> unprocessable("Documento dell'operazione mancante"));
        return storage.get(objectKey);
    }

    private String validationError(ExternalDeliveryRepository.Preparation p) {
        if (p.documentId() == null || p.objectKey() == null) return "Documento firmato attivo mancante";
        if (p.facilityCode() == null || p.operatingUnit() == null) return "Mapping FSE del presidio mancante";
        return null;
    }

    private DeliveryMetadata metadata(ExternalDeliveryRepository.Preparation p) {
        return new DeliveryMetadata(p.reportIdentifier(), p.documentTypeCode(), p.facilityCode(), p.facilityName(),
                p.operatingUnit(), p.department(), p.sourceSystemCode(), p.sha256());
    }

    private void requireState(ExternalDeliveryRepository.Preparation p, Channel channel) {
        String required = channel == Channel.FSE ? "SIGNED" : "FSE_ACCEPTED";
        if (!required.equals(p.reportState())) throw conflict(channel + " richiede lo stato " + required);
    }

    private void requireReportVersion(OperationSummary summary, long expected) {
        if (summary.reportVersion() != expected) throw conflict("Il referto e stato aggiornato; versione corrente " + summary.reportVersion());
    }

    private State sentState(Channel channel) { return channel == Channel.FSE ? State.FSE_SENT : State.CONSERVATION_SENT; }
    private State resultState(Channel channel, AdapterResult.Status status) {
        if (status == AdapterResult.Status.TIMEOUT) return State.TIMEOUT;
        if (channel == Channel.FSE) return status == AdapterResult.Status.ACCEPTED ? State.FSE_ACCEPTED
                : status == AdapterResult.Status.REJECTED ? State.FSE_REJECTED : State.FSE_SENT;
        return status == AdapterResult.Status.ACCEPTED ? State.CONSERVATION_ACCEPTED
                : status == AdapterResult.Status.REJECTED ? State.CONSERVATION_REJECTED : State.CONSERVATION_SENT;
    }
    private ReportState reportState(State state) {
        return switch (state) {
            case TIMEOUT -> throw new IllegalArgumentException("TIMEOUT requires channel context");
            default -> ReportState.valueOf(state.name());
        };
    }
    private ReportState reportStateForTimeout(Channel channel) { return channel == Channel.FSE ? ReportState.FSE_SENT : ReportState.CONSERVATION_SENT; }
    private ReportState reportState(State state, Channel channel) { return state == State.TIMEOUT ? reportStateForTimeout(channel) : reportState(state); }
    private String outcome(AdapterResult.Status status) {
        return switch (status) { case PENDING -> "PENDING"; case ACCEPTED, VALID -> "SUCCESS"; case TIMEOUT -> "TIMEOUT"; case REJECTED -> "FAILURE"; };
    }
    private ExternalDeliveryRepository.StoredOperation operation(UUID id, boolean lock) {
        return repository.find(id, lock).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Operazione non trovata"));
    }
    private String actor(String value) { return value == null || value.isBlank() ? "unknown" : value.trim(); }
    private String key(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Operation key is required"); return value.trim(); }
    private String workflowKey(UUID operationId, String key) { return "external-" + operationId + "-" + Integer.toHexString(key.hashCode()); }
    private String fingerprint(String action, UUID id, long version) { return sha256((action + "|" + id + "|" + version).getBytes(StandardCharsets.UTF_8)); }
    private String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException unprocessable(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }

    public record ReceiptContent(String filename, String mimeType, byte[] bytes) {}
}
