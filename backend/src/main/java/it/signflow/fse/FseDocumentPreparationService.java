package it.signflow.fse;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FseDocumentPreparationService {
    private final FsePreparationRepository repository;
    private final CdaBuilder cdaBuilder;
    private final PdfCdaInjector injector;

    public FseDocumentPreparationService(FsePreparationRepository repository, CdaBuilder cdaBuilder,
                                         PdfCdaInjector injector) {
        this.repository = repository;
        this.cdaBuilder = cdaBuilder;
        this.injector = injector;
    }

    public FsePreparationDecision evaluate(UUID reportId) {
        FsePreparationRepository.Configuration configuration = repository.configuration(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        boolean required = configuration.sourceActive() && configuration.createCda()
                && !configuration.passthrough() && configuration.typeEnabled();
        String reason = required ? "CDA injection enabled for source system and FSE document type"
                : "CDA injection not enabled for this source system and FSE document type";
        return new FsePreparationDecision(reportId, configuration.documentTypeCode(), required, reason);
    }

    public PreparationResult prepare(UUID reportId, byte[] pdf, CdaBuildRequest request) {
        FsePreparationDecision decision = evaluate(reportId);
        if (!decision.cdaInjectionRequired()) return new PreparationResult(PreparationStatus.NOT_REQUIRED, pdf);
        return cdaBuilder.build(request)
                .map(cda -> new PreparationResult(PreparationStatus.INJECTED, injector.inject(pdf, cda)))
                .orElseGet(() -> new PreparationResult(PreparationStatus.PENDING_CDA_GENERATION, null));
    }

    public byte[] injectExistingCda(UUID reportId, byte[] pdf, byte[] cdaXml) {
        FsePreparationDecision decision = evaluate(reportId);
        if (!decision.cdaInjectionRequired()) {
            throw new IllegalStateException("CDA injection is not configured for this report type");
        }
        return injector.inject(pdf, cdaXml);
    }

    public enum PreparationStatus { NOT_REQUIRED, PENDING_CDA_GENERATION, INJECTED }
    public record PreparationResult(PreparationStatus status, byte[] pdf) {}
}
