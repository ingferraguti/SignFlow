package it.signflow.reports;

import it.signflow.identity.PageResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SignerReportService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Rome");
    private static final int MAX_PAGE_SIZE = 100;
    private final SignerReportRepository repository;
    private final ClinicalDocumentService documentService;
    private final ReportWorkflowService workflowService;

    public SignerReportService(SignerReportRepository repository, ClinicalDocumentService documentService,
                               ReportWorkflowService workflowService) {
        this.repository = repository;
        this.documentService = documentService;
        this.workflowService = workflowService;
    }

    public SignerHomeResponse home(String username) {
        requireSigner(username);
        return repository.home(username);
    }

    public PageResponse<ReportSummaryResponse> search(String username, SignerReportSearchRequest request) {
        requireSigner(username);
        if (request.page() < 0 || request.size() < 1 || request.size() > MAX_PAGE_SIZE) {
            throw badRequest("page must be >= 0 and size must be between 1 and 100");
        }
        validateRange("production", request.producedFrom(), request.producedTo());
        validateRange("signature", request.signedFrom(), request.signedTo());
        SignerReportRepository.SignerCriteria criteria = new SignerReportRepository.SignerCriteria(
                text(request.query()), text(request.patient()), text(request.documentType()), text(request.department()),
                parseState(request.state()), from(request.producedFrom()), toExclusive(request.producedTo()),
                from(request.signedFrom()), toExclusive(request.signedTo()), request.page(), request.size());
        return repository.search(username, criteria);
    }

    public ReportDetailResponse detail(String username, UUID reportId) {
        requireSigner(username);
        return visible(username, reportId);
    }

    public List<ClinicalDocumentResponse> documents(String username, UUID reportId) {
        requireSigner(username);
        visible(username, reportId);
        return documentService.list(reportId, false);
    }

    @Transactional
    public SignerPreviewResponse preview(String username, UUID reportId, UUID documentId,
                                         PreviewRegistrationRequest request) {
        requireSigner(username);
        visible(username, reportId);
        TemporaryDocumentUrlResponse temporary = documentService.temporaryUrl(reportId, documentId, "inline");
        ReportWorkflowOperationResponse operation = workflowService.registerFirstPreview(reportId, request, username);
        return new SignerPreviewResponse(temporary.url(), temporary.expiresAt(), operation.toState(),
                operation.resultingVersion(), operation.firstPreviewedAt());
    }

    public ClinicalDocumentService.DocumentContent download(String username, UUID reportId, UUID documentId) {
        requireSigner(username);
        visible(username, reportId);
        return documentService.content(reportId, documentId);
    }

    public SignerProfileResponse profile(String username) {
        requireSigner(username);
        return repository.profile(username).orElseThrow(() -> forbidden("Signer profile not available"));
    }

    public List<ReportStateLegendResponse> legend() {
        return List.of(
                legend(ReportState.RECEIVED, "Ricevuto", "Il referto è stato acquisito."),
                legend(ReportState.PARSED, "Interpretato", "I metadati sono stati elaborati."),
                legend(ReportState.INCOMPLETE, "Incompleto", "Mancano dati necessari alla lavorazione."),
                legend(ReportState.MISSING_SIGNER, "Firmatario mancante", "Il firmatario non è ancora disponibile."),
                legend(ReportState.READY_TO_SIGN, "Da firmare", "Il referto è pronto per la consultazione e la firma."),
                legend(ReportState.PREVIEWED, "Visualizzato", "Il PDF è stato aperto dal firmatario."),
                legend(ReportState.REVIEW_PENDING, "Revisione richiesta", "Il referto richiede una revisione."),
                legend(ReportState.APPROVED, "Approvato", "La revisione è stata approvata."),
                legend(ReportState.SIGN_BATCH_CREATED, "In lotto", "Il referto è incluso in un lotto di firma."),
                legend(ReportState.SIGNING, "Firma in corso", "La richiesta di firma è in elaborazione."),
                legend(ReportState.SIGNED, "Firmato", "La firma è stata completata."),
                legend(ReportState.SIGN_ERROR, "Errore firma", "La firma non è riuscita."),
                legend(ReportState.FSE_VALIDATION_ERROR, "Errore validazione FSE", "Il documento non supera la validazione FSE."),
                legend(ReportState.FSE_SENT, "Inviato a FSE", "Il documento è stato inviato a FSE."),
                legend(ReportState.FSE_ACCEPTED, "Accettato da FSE", "FSE ha accettato il documento."),
                legend(ReportState.FSE_REJECTED, "Rifiutato da FSE", "FSE ha rifiutato il documento."),
                legend(ReportState.CONSERVATION_SENT, "Inviato in conservazione", "Il documento è stato inviato in conservazione."),
                legend(ReportState.CONSERVATION_ACCEPTED, "Conservato", "La conservazione ha accettato il documento."));
    }

    private ReportDetailResponse visible(String username, UUID reportId) {
        return repository.findVisible(username, reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    private void requireSigner(String username) {
        if (username == null || username.isBlank() || !repository.activeSignerExists(username)) {
            throw forbidden("Signer profile not available");
        }
    }

    private ReportState parseState(String state) {
        String value = text(state);
        if (value == null) return null;
        try {
            return ReportState.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw badRequest("Unsupported report state");
        }
    }

    private void validateRange(String label, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw badRequest("Invalid " + label + " date interval");
    }

    private OffsetDateTime from(LocalDate value) {
        return value == null ? null : value.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
    }

    private OffsetDateTime toExclusive(LocalDate value) {
        return value == null ? null : value.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ReportStateLegendResponse legend(ReportState state, String label, String description) {
        return new ReportStateLegendResponse(state, label, description);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
