package it.signflow.ingestion;

import it.signflow.ingestion.ServiceSignatureRequestRepository.ArtifactType;
import it.signflow.reports.DocumentStorageProperties;
import it.signflow.reports.MinioObjectStorage;
import it.signflow.signatures.DigitalSignatureEngine;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServiceSignatureRequestService {
    private static final Set<ServiceConservationStatus> TERMINAL_CONSERVATION = Set.of(
            ServiceConservationStatus.ACCEPTED, ServiceConservationStatus.REJECTED, ServiceConservationStatus.FAILED);
    private final ServiceSignatureRequestRepository repository;
    private final MinioObjectStorage storage;
    private final DocumentStorageProperties storageProperties;
    private final ServicePdfA3Normalizer normalizer;
    private final DigitalSignatureEngine signatureEngine;

    ServiceSignatureRequestService(ServiceSignatureRequestRepository repository, MinioObjectStorage storage,
            DocumentStorageProperties storageProperties, ServicePdfA3Normalizer normalizer,
            DigitalSignatureEngine signatureEngine) {
        this.repository = repository; this.storage = storage; this.storageProperties = storageProperties;
        this.normalizer = normalizer; this.signatureEngine = signatureEngine;
    }

    public List<ServiceDocumentTypeCatalogResponse> catalog() {
        List<ServiceSignatureRequestRepository.CatalogSubtype> catalog = repository.catalog();
        List<ServiceDocumentTypeCatalogResponse> response = new ArrayList<>();
        for (ServiceDocumentType type : ServiceDocumentType.values()) {
            var subtypes = catalog.stream().filter(item -> item.type() == type)
                    .map(item -> new ServiceDocumentTypeCatalogResponse.Subtype(
                            item.code(), item.displayName(), item.sourceReference())).toList();
            response.add(new ServiceDocumentTypeCatalogResponse(type,
                    type == ServiceDocumentType.HEALTHCARE ? "Documento sanitario" : "Documento amministrativo",
                    subtypes));
        }
        return List.copyOf(response);
    }

    @Transactional
    public ServiceSignatureRequestResponse submit(ServiceSignatureRequestInput input, MultipartFile file,
            String sourceSystemCode, String suppliedIdempotencyKey, String suppliedCorrelationId, String actor) {
        var source = source(sourceSystemCode);
        String externalId = requiredText(input.externalRequestId(), "externalRequestId is required");
        String subtype = input.documentSubtype().trim().toUpperCase(Locale.ROOT);
        if (!repository.activeSubtype(input.documentType(), subtype)) {
            throw unprocessable("Document subtype is not admitted for document type " + input.documentType());
        }
        var signerInput = input.signer();
        String country = signerInput.issuingCountry().trim().toUpperCase(Locale.ROOT);
        String issuer = signerInput.issuer().trim();
        String signerValue = normalizeIdentifier(signerInput.scheme(), signerInput.value());
        var signer = repository.activeSigner(signerInput.scheme(), country, issuer, signerValue)
                .orElseThrow(() -> unprocessable("The specified signer is missing, inactive, or not enabled to sign"));
        Upload sourceDocument = upload(file);
        var normalized = normalizer.normalize(sourceDocument.content(), sourceDocument.declaredContentType(),
                sourceDocument.filename(), storageProperties.maxSizeBytes());
        String idempotencyKey = idempotency(suppliedIdempotencyKey, externalId);
        String correlationId = correlation(suppliedCorrelationId);
        String normalizedActor = actor(actor);
        String fingerprint = sha256(String.join("|", externalId, input.documentType().name(), subtype,
                signerInput.scheme().name(), country, issuer.toUpperCase(Locale.ROOT), signerValue,
                sourceDocument.filename(), sourceDocument.sha256()));

        var prior = repository.byIdempotency(source.id(), idempotencyKey);
        if (prior.isPresent()) return repeat(prior.get(), fingerprint);
        if (repository.byExternalRequestId(source.id(), externalId).isPresent()) {
            throw conflict("externalRequestId is already used for this SourceSystem");
        }

        UUID requestId = UUID.randomUUID();
        UUID originalId = UUID.randomUUID();
        UUID normalizedId = UUID.randomUUID();
        String originalKey = "signature-requests/" + requestId + "/" + originalId + ".source";
        String normalizedKey = "signature-requests/" + requestId + "/" + normalizedId + ".pdf";
        storage.put(originalKey, new ByteArrayInputStream(sourceDocument.content()), sourceDocument.content().length,
                normalized.sourceContentType());
        boolean originalKept = false;
        boolean normalizedKept = false;
        try {
            storage.put(normalizedKey, new ByteArrayInputStream(normalized.content()), normalized.content().length,
                    "application/pdf");
            boolean inserted = repository.insertRequest(requestId, externalId, source, input.documentType(), subtype,
                    signer.id(), idempotencyKey, fingerprint, correlationId, normalizedActor);
            if (!inserted) {
                var collision = repository.byIdempotency(source.id(), idempotencyKey);
                if (collision.isPresent()) return repeat(collision.get(), fingerprint);
                throw conflict("externalRequestId is already used for this SourceSystem");
            }
            repository.insertDocument(originalId, requestId, ArtifactType.ORIGINAL, sourceDocument.sha256(),
                    sourceDocument.content().length, sourceDocument.filename(), normalized.sourceContentType(),
                    originalKey, null, null, null, null);
            repository.insertDocument(normalizedId, requestId, ArtifactType.NORMALIZED_PDFA3, normalized.sha256(),
                    normalized.content().length, sourceDocument.filename(), "application/pdf", normalizedKey,
                    normalized.pdfaPart(), normalized.pdfaConformance(), normalized.validator(), normalized.pageCount());
            originalKept = true; normalizedKept = true;
            return response(repository.byId(source.id(), requestId).orElseThrow(), false);
        } finally {
            if (!normalizedKept) storage.remove(normalizedKey);
            if (!originalKept) storage.remove(originalKey);
        }
    }

    public ServiceSignatureRequestResponse find(UUID requestId, String sourceSystemCode) {
        var source = source(sourceSystemCode);
        return response(repository.byId(source.id(), requestId).orElseThrow(() -> notFound()), false);
    }

    public StoredContent signableDocument(UUID requestId) {
        var stored = repository.byId(requestId, false).orElseThrow(this::notFound);
        var document = stored.normalized();
        return new StoredContent(document.filename(), document.mimeType(), document.sha256(),
                storage.get(document.objectKey()));
    }

    public StoredContent signedDocument(UUID requestId, String sourceSystemCode) {
        var source = source(sourceSystemCode);
        var stored = repository.byId(source.id(), requestId).orElseThrow(this::notFound);
        if (stored.signed() == null || !"SIGNED".equals(stored.status())) {
            throw conflict("Signed document is not available; current status is " + stored.status());
        }
        var document = stored.signed();
        return new StoredContent(document.filename(), document.mimeType(), document.sha256(),
                storage.get(document.objectKey()));
    }

    @Transactional
    public ServiceSignatureRequestResponse registerSignedDocument(UUID requestId, MultipartFile file,
            String suppliedIdempotencyKey, String actor) {
        String key = requiredIdempotency(suppliedIdempotencyKey);
        Upload upload = signedPdf(file);
        String fingerprint = sha256("REGISTER_SIGNED_DOCUMENT|" + upload.sha256());
        var stored = repository.byId(requestId, true).orElseThrow(this::notFound);
        var prior = repository.command(requestId, key);
        if (prior.isPresent()) return repeatedCommand(stored, prior.get(), "REGISTER_SIGNED_DOCUMENT", fingerprint);
        if (stored.signed() != null || "SIGNED".equals(stored.status())) {
            throw conflict("A signed document is already registered");
        }
        byte[] unsigned = storage.get(stored.normalized().objectKey());
        DigitalSignatureEngine.VerificationResult verification;
        try { verification = signatureEngine.verifyPdfAgainst(upload.content(), unsigned, List.of()); }
        catch (IllegalArgumentException exception) { throw unprocessable("Signed PDF validation failed"); }
        if (!verification.signed() || !verification.technicallyValid()) {
            throw unprocessable("PDF does not contain only technically valid PAdES signatures");
        }
        if (!verification.originalDocumentMatches()) {
            throw unprocessable("Signed PDF does not cover the normalized document of this request");
        }
        OffsetDateTime signedAt = verification.signatures().stream().map(DigitalSignatureEngine.SignatureInformation::signingTime)
                .filter(java.util.Objects::nonNull).min(OffsetDateTime::compareTo)
                .orElseGet(() -> OffsetDateTime.now(ZoneOffset.UTC));
        String validation = verification.valid() ? "TRUSTED_VALID" : "TECHNICALLY_VALID";
        UUID documentId = UUID.randomUUID();
        String objectKey = "signature-requests/" + requestId + "/" + documentId + "-signed.pdf";
        storage.put(objectKey, new ByteArrayInputStream(upload.content()), upload.content().length, "application/pdf");
        boolean keep = false;
        try {
            repository.insertDocument(documentId, requestId, ArtifactType.SIGNED, upload.sha256(),
                    upload.content().length, stored.original().filename(), "application/pdf", objectKey,
                    null, null, null, null);
            if (!repository.markSigned(requestId, stored.version(), signedAt, verification.signatureCount(), validation)) {
                throw conflict("Signature request was updated concurrently");
            }
            repository.insertEvent(requestId, "SIGNED_DOCUMENT_REGISTERED", "SIGNED", actor(actor),
                    stored.correlationId(), signedAt);
            repository.insertCommand(requestId, key, "REGISTER_SIGNED_DOCUMENT", fingerprint);
            keep = true;
            return response(repository.byId(requestId, false).orElseThrow(), false);
        } finally {
            if (!keep) storage.remove(objectKey);
        }
    }

    @Transactional
    public ServiceSignatureRequestResponse registerConservation(UUID requestId, ConservationStatusInput input,
            String suppliedIdempotencyKey, String actor) {
        String key = requiredIdempotency(suppliedIdempotencyKey);
        ServiceConservationStatus target = input.status();
        if (target == ServiceConservationStatus.NOT_REQUESTED) throw badRequest("NOT_REQUESTED cannot be reported by an adapter");
        OffsetDateTime occurredAt = input.occurredAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : input.occurredAt();
        String reference = text(input.remoteReference());
        String errorCode = text(input.errorCode());
        if ((target == ServiceConservationStatus.SENT || target == ServiceConservationStatus.ACCEPTED) && reference == null) {
            throw badRequest("remoteReference is required for SENT or ACCEPTED");
        }
        String fingerprint = sha256(String.join("|", "REGISTER_CONSERVATION_STATUS", target.name(),
                nullToEmpty(reference), nullToEmpty(errorCode), occurredAt.toString()));
        var stored = repository.byId(requestId, true).orElseThrow(this::notFound);
        var prior = repository.command(requestId, key);
        if (prior.isPresent()) return repeatedCommand(stored, prior.get(),
                "REGISTER_CONSERVATION_STATUS", fingerprint);
        if (!"SIGNED".equals(stored.status())) throw conflict("Conservation status requires a signed document");
        if (TERMINAL_CONSERVATION.contains(stored.conservationStatus())) {
            throw conflict("Conservation status is already terminal: " + stored.conservationStatus());
        }
        if (!allowedTransition(stored.conservationStatus(), target)) {
            throw conflict("Invalid conservation transition from " + stored.conservationStatus() + " to " + target);
        }
        boolean sent = stored.conservationSentAt() != null || target == ServiceConservationStatus.SENT
                || target == ServiceConservationStatus.ACCEPTED || target == ServiceConservationStatus.REJECTED;
        if (!repository.updateConservation(requestId, stored.version(), target, sent, occurredAt, reference, errorCode)) {
            throw conflict("Signature request was updated concurrently");
        }
        repository.insertEvent(requestId, "CONSERVATION_" + target.name(), target.name(), actor(actor),
                stored.correlationId(), occurredAt);
        repository.insertCommand(requestId, key, "REGISTER_CONSERVATION_STATUS", fingerprint);
        return response(repository.byId(requestId, false).orElseThrow(), false);
    }

    private boolean allowedTransition(ServiceConservationStatus current, ServiceConservationStatus target) {
        return switch (current) {
            case NOT_REQUESTED -> target == ServiceConservationStatus.PENDING || target == ServiceConservationStatus.SENT;
            case PENDING -> target == ServiceConservationStatus.SENT || target == ServiceConservationStatus.FAILED;
            case SENT -> target == ServiceConservationStatus.ACCEPTED || target == ServiceConservationStatus.REJECTED
                    || target == ServiceConservationStatus.FAILED;
            default -> false;
        };
    }

    private ServiceSignatureRequestResponse repeatedCommand(ServiceSignatureRequestRepository.StoredRequest stored,
            ServiceSignatureRequestRepository.StoredCommand prior, String type, String fingerprint) {
        if (!prior.commandType().equals(type) || !prior.fingerprint().equals(fingerprint)) {
            throw conflict("X-Idempotency-Key was already used with different command content");
        }
        return response(stored, true);
    }

    private ServiceSignatureRequestResponse repeat(ServiceSignatureRequestRepository.StoredRequest stored,
                                                    String fingerprint) {
        if (!stored.fingerprint().equals(fingerprint)) {
            throw conflict("X-Idempotency-Key was already used with different request content");
        }
        return response(stored, true);
    }

    private ServiceSignatureRequestResponse response(ServiceSignatureRequestRepository.StoredRequest stored,
                                                     boolean idempotent) {
        var original = stored.original(); var normalized = stored.normalized(); var signed = stored.signed();
        boolean wasSent = stored.conservationSentAt() != null;
        return new ServiceSignatureRequestResponse(stored.id(), stored.externalRequestId(), stored.sourceSystemCode(),
                stored.documentType(), stored.documentSubtype(), stored.documentSubtypeDisplayName(), stored.status(),
                new ServiceSignatureRequestResponse.Signer(stored.signerId(), stored.signerDisplayName()),
                new ServiceSignatureRequestResponse.SourceDocument(original.filename(), original.mimeType(),
                        original.sizeBytes(), original.sha256()),
                new ServiceSignatureRequestResponse.NormalizedDocument(normalized.id(), normalized.filename(),
                        normalized.mimeType(), normalized.sizeBytes(), normalized.sha256(), ServicePdfA3Normalizer.PROFILE,
                        normalized.pdfaPart(), normalized.pdfaConformance(), normalized.pdfaValidator(), normalized.pageCount()),
                new ServiceSignatureRequestResponse.Signature(signed != null, stored.signedAt(), stored.signatureCount(),
                        stored.signatureValidation(), signed != null, signed == null ? null : signed.sha256(),
                        signed == null ? null : "/api/integration/signature-requests/" + stored.id() + "/signed-document"),
                new ServiceSignatureRequestResponse.Conservation(stored.conservationStatus().name(), wasSent,
                        stored.conservationSentAt(), stored.conservationCompletedAt(),
                        stored.conservationRemoteReference(), stored.conservationErrorCode()),
                stored.correlationId(), stored.submittedAt(), idempotent);
    }

    private Upload upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw badRequest("A non-empty document is required");
        String filename = safeFilename(file.getOriginalFilename());
        try {
            byte[] content = file.getBytes();
            size(content);
            return new Upload(filename, file.getContentType(), content, sha256(content));
        } catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { throw badRequest("Unable to read uploaded document"); }
    }

    private Upload signedPdf(MultipartFile file) {
        Upload upload = upload(file);
        if (upload.declaredContentType() != null && !upload.declaredContentType().toLowerCase(Locale.ROOT)
                .startsWith("application/pdf")) throw badRequest("Signed document must use application/pdf");
        if (upload.content().length < 5 || upload.content()[0] != '%' || upload.content()[1] != 'P'
                || upload.content()[2] != 'D' || upload.content()[3] != 'F' || upload.content()[4] != '-') {
            throw badRequest("Signed document is not a PDF");
        }
        return upload;
    }

    private void size(byte[] content) {
        if (content.length > storageProperties.maxSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Document exceeds the configured limit of " + storageProperties.maxSizeBytes() + " bytes");
        }
    }

    private String safeFilename(String filename) {
        String value = requiredText(filename, "Original filename is required");
        if (value.length() > 255 || value.contains("/") || value.contains("\\")
                || value.chars().anyMatch(Character::isISOControl)) throw badRequest("Unsafe original filename");
        return value;
    }

    private ServiceSignatureRequestRepository.SourceSystem source(String code) {
        return repository.activeSourceSystem(requiredText(code, "X-Source-System is required"))
                .orElseThrow(() -> unprocessable("SourceSystem is missing, inactive, or unknown"));
    }
    private String idempotency(String supplied, String fallback) {
        String value = text(supplied) == null ? fallback : supplied.trim();
        if (value.length() > 160) throw badRequest("X-Idempotency-Key exceeds 160 characters");
        return value;
    }
    private String requiredIdempotency(String value) {
        String result = requiredText(value, "X-Idempotency-Key is required");
        if (result.length() > 160) throw badRequest("X-Idempotency-Key exceeds 160 characters");
        return result;
    }
    private String normalizeIdentifier(PersonIdentifierScheme scheme, String value) {
        String normalized = requiredText(value, "Signer identifier value is required");
        return scheme == PersonIdentifierScheme.IT_TAX_CODE ? normalized.toUpperCase(Locale.ROOT) : normalized;
    }
    private String correlation(String value) {
        String result = text(value) == null ? UUID.randomUUID().toString() : value.trim();
        if (!result.matches("[A-Za-z0-9._:-]{1,160}")) throw badRequest("Invalid X-Correlation-ID");
        return result;
    }
    private String actor(String value) { return text(value) == null ? "system.integration" : value.trim(); }
    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private String requiredText(String value, String message) {
        if (text(value) == null) throw badRequest(message); return value.trim();
    }
    private String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException unprocessable(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Signature request not found"); }

    public record StoredContent(String filename, String mimeType, String sha256, byte[] bytes) {
        public StoredContent { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    private record Upload(String filename, String declaredContentType, byte[] content, String sha256) {
        private Upload { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
}
