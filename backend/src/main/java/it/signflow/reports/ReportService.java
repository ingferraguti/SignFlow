package it.signflow.reports;

import it.signflow.identity.PageResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Rome");
    private static final Set<String> SORT_FIELDS = Set.of(
            "producedAt", "modifiedAt", "signedAt", "internalIdentifier", "state");

    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }

    public PageResponse<ReportSummaryResponse> search(AdminReportSearchRequest request) {
        validatePagingAndSorting(request);
        validateRange("production", request.producedFrom(), request.producedTo());
        validateRange("modification", request.modifiedFrom(), request.modifiedTo());
        validateRange("signature", request.signedFrom(), request.signedTo());

        ReportLookupType lookupType = ReportLookupType.NONE;
        String exactIdentifier = null;
        if (text(request.internalIdentifier()) != null) {
            lookupType = ReportLookupType.INTERNAL;
            exactIdentifier = text(request.internalIdentifier());
        } else if (text(request.externalIdentifier()) != null) {
            lookupType = ReportLookupType.EXTERNAL;
            exactIdentifier = text(request.externalIdentifier());
        } else if (text(request.fseIdentifier()) != null) {
            lookupType = ReportLookupType.FSE;
            exactIdentifier = text(request.fseIdentifier());
        }

        ReportSearchCriteria criteria = new ReportSearchCriteria(
                lookupType, exactIdentifier, text(request.patient()), text(request.signer()),
                text(request.signerFiscalCode()), parseState(request.state()), request.sourceSystemId(),
                text(request.department()), from(request.producedFrom()), toExclusive(request.producedTo()),
                from(request.modifiedFrom()), toExclusive(request.modifiedTo()), from(request.signedFrom()),
                toExclusive(request.signedTo()), request.page(), request.size(), request.sortBy(), request.direction());
        return repository.search(criteria);
    }

    public ReportDetailResponse get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    private void validatePagingAndSorting(AdminReportSearchRequest request) {
        if (request.page() < 0 || request.size() < 1 || request.size() > MAX_PAGE_SIZE) {
            throw badRequest("page must be >= 0 and size must be between 1 and 100");
        }
        if (!SORT_FIELDS.contains(request.sortBy())) {
            throw badRequest("Unsupported sortBy value");
        }
        if (!("asc".equals(request.direction()) || "desc".equals(request.direction()))) {
            throw badRequest("direction must be asc or desc");
        }
    }

    private void validateRange(String label, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw badRequest("Invalid " + label + " date interval");
        }
    }

    private ReportState parseState(String state) {
        String normalized = text(state);
        if (normalized == null) return null;
        try {
            return ReportState.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw badRequest("Unsupported report state");
        }
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

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
