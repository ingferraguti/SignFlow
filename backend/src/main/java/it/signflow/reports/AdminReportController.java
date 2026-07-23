package it.signflow.reports;

import it.signflow.identity.PageResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {
    private final ReportService service;

    public AdminReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<ReportSummaryResponse> search(
            @RequestParam(required = false) String internalIdentifier,
            @RequestParam(required = false) String externalIdentifier,
            @RequestParam(required = false) String fseIdentifier,
            @RequestParam(required = false) String patient,
            @RequestParam(required = false) String signer,
            @RequestParam(required = false) String signerFiscalCode,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) UUID sourceSystemId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate producedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate producedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate modifiedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate modifiedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "producedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.search(new AdminReportSearchRequest(
                internalIdentifier, externalIdentifier, fseIdentifier, patient, signer, signerFiscalCode,
                state, sourceSystemId, department, producedFrom, producedTo, modifiedFrom, modifiedTo,
                signedFrom, signedTo, page, size, sortBy, direction));
    }

    @GetMapping("/{id}")
    ReportDetailResponse detail(@PathVariable UUID id) {
        return service.get(id);
    }
}
