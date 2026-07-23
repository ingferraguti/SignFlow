package it.signflow.reports;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClinicalDocumentService {
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_EOF = "%%EOF".getBytes(StandardCharsets.US_ASCII);

    private final ClinicalDocumentRepository repository;
    private final MinioObjectStorage storage;
    private final DocumentStorageProperties properties;

    public ClinicalDocumentService(ClinicalDocumentRepository repository, MinioObjectStorage storage,
                                   DocumentStorageProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
    }

    public List<ClinicalDocumentResponse> list(UUID reportId, boolean includeDeleted) {
        requireReport(reportId);
        return repository.list(reportId, includeDeleted);
    }

    public ClinicalDocumentResponse upload(UUID reportId, MultipartFile file, String uploader) {
        if (file == null || file.isEmpty()) throw badRequest("A non-empty PDF is required");
        String filename = validateFilename(file.getOriginalFilename());
        String declaredType = file.getContentType();
        if (declaredType != null && !declaredType.isBlank() && !"application/pdf".equalsIgnoreCase(declaredType)) {
            throw badRequest("Only application/pdf uploads are supported");
        }
        try {
            return upload(reportId, filename, file.getBytes(), uploader);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded PDF", exception);
        }
    }

    @Transactional
    public ClinicalDocumentResponse upload(UUID reportId, String filename, byte[] content, String uploader) {
        requireReport(reportId);
        validateContent(content);
        String safeFilename = validateFilename(filename);
        UUID id = UUID.randomUUID();
        String objectKey = "reports/" + reportId + "/" + id + ".pdf";
        String hash = sha256(content);
        storage.put(objectKey, new ByteArrayInputStream(content), content.length, "application/pdf");
        try {
            return repository.insert(id, reportId, hash, content.length, repository.nextVersion(reportId),
                    safeFilename, objectKey, normalizedUser(uploader));
        } catch (RuntimeException exception) {
            storage.remove(objectKey);
            throw exception;
        }
    }

    public DocumentContent content(UUID reportId, UUID documentId) {
        ClinicalDocumentResponse document = active(reportId, documentId);
        return new DocumentContent(document, storage.get(document.objectIdentifier()));
    }

    public TemporaryDocumentUrlResponse temporaryUrl(UUID reportId, UUID documentId, String disposition) {
        ClinicalDocumentResponse document = active(reportId, documentId);
        String safeDisposition = validateDisposition(disposition);
        int seconds = Math.max(1, Math.min(properties.temporaryUrlSeconds(), 3600));
        String url = storage.temporaryGetUrl(document.objectIdentifier(), safeDisposition, seconds,
                document.originalFilename().replace("\"", ""));
        return new TemporaryDocumentUrlResponse(url, OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(seconds));
    }

    @Transactional
    public void delete(UUID reportId, UUID documentId, String user) {
        active(reportId, documentId);
        repository.logicalDelete(reportId, documentId, normalizedUser(user));
    }

    private ClinicalDocumentResponse active(UUID reportId, UUID documentId) {
        requireReport(reportId);
        return repository.find(reportId, documentId, false)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinical document not found"));
    }

    private void requireReport(UUID reportId) {
        if (!repository.reportExists(reportId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
    }

    private void validateContent(byte[] content) {
        if (content.length == 0) throw badRequest("A non-empty PDF is required");
        if (content.length > properties.maxSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "PDF exceeds the configured limit of " + properties.maxSizeBytes() + " bytes");
        }
        if (!startsWith(content, PDF_SIGNATURE) || !containsNearEnd(content, PDF_EOF)) {
            throw badRequest("File content is not a valid PDF");
        }
    }

    private String validateFilename(String filename) {
        if (filename == null || filename.isBlank()) throw badRequest("Original filename is required");
        String trimmed = filename.trim();
        if (trimmed.length() > 255 || trimmed.contains("/") || trimmed.contains("\\")
                || trimmed.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw badRequest("Unsafe original filename");
        }
        if (!trimmed.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw badRequest("PDF filename must use the .pdf extension");
        }
        return trimmed;
    }

    private boolean startsWith(byte[] content, byte[] expected) {
        if (content.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++) if (content[index] != expected[index]) return false;
        return true;
    }

    private boolean containsNearEnd(byte[] content, byte[] expected) {
        int start = Math.max(0, content.length - 1024);
        outer: for (int index = start; index <= content.length - expected.length; index++) {
            for (int offset = 0; offset < expected.length; offset++) {
                if (content[index + offset] != expected[offset]) continue outer;
            }
            return true;
        }
        return false;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizedUser(String user) {
        return user == null || user.isBlank() ? "unknown" : user.trim();
    }

    private String validateDisposition(String disposition) {
        if (disposition == null || disposition.isBlank() || "inline".equalsIgnoreCase(disposition)) return "inline";
        if ("attachment".equalsIgnoreCase(disposition)) return "attachment";
        throw badRequest("Disposition must be inline or attachment");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record DocumentContent(ClinicalDocumentResponse metadata, byte[] bytes) {
    }
}
