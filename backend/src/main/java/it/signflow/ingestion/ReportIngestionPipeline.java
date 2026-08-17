package it.signflow.ingestion;

import it.signflow.fse.CdaBuildRequest;
import it.signflow.fse.CdaBuilder;
import it.signflow.reports.ClinicalDocumentResponse;
import it.signflow.reports.ClinicalDocumentService;
import it.signflow.reports.ReportState;
import it.signflow.reports.ReportWorkflowService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
final class ReportIngestionPipeline {
    private final IngestionRepository repository;
    private final ClinicalDocumentService documents;
    private final ReportWorkflowService workflow;
    private final CdaBuilder cdaBuilder;
    private final DocumentNormalizer normalizer;
    private final PdfA3Converter pdfA3Converter;

    ReportIngestionPipeline(IngestionRepository repository, ClinicalDocumentService documents,
                            ReportWorkflowService workflow, CdaBuilder cdaBuilder,
                            DocumentNormalizer normalizer, PdfA3Converter pdfA3Converter) {
        this.repository = repository;
        this.documents = documents;
        this.workflow = workflow;
        this.cdaBuilder = cdaBuilder;
        this.normalizer = normalizer;
        this.pdfA3Converter = pdfA3Converter;
    }

    PipelineResult process(UUID messageId, ParsedHl7Message parsed, SourceSystemPipeline source,
                           String correlationId) {
        List<String> missing = missingFields(parsed);
        String documentType = normalizeDocumentType(parsed.documentType(), missing);
        String externalIdentifier = text(parsed.externalReportIdentifier());
        repository.reportByExternalIdentifier(externalIdentifier).ifPresent(existing -> {
            throw new PipelineDiscardException("REPORT_ALREADY_EXISTS",
                    "A Report with the same external identifier already exists", existing);
        });

        UUID practiceId = repository.practice(identifier("CASE", parsed.episodeIdentifier(), messageId));
        UUID patientId = repository.patient(identifier("PATIENT", parsed.patientIdentifier(), messageId),
                fallback(parsed.patientFirstName()), fallback(parsed.patientLastName()),
                text(parsed.patientFiscalCode()), parsed.patientBirthDate());
        UUID signerId = repository.signer(parsed.signerIdentifier()).orElse(null);
        UUID reportId = UUID.randomUUID();
        String internalIdentifier = internalIdentifier(source.code(), parsed.controlId(), messageId);
        OffsetDateTime producedAt = parsed.producedAt() == null ? OffsetDateTime.now() : parsed.producedAt();
        repository.insertReport(reportId, internalIdentifier, externalIdentifier, practiceId, patientId, signerId,
                source, documentType, fallback(parsed.department()), producedAt);

        List<String> steps = new ArrayList<>();
        steps.add("REPORT_RECEIVED");
        workflow.transitionForIngestion(reportId, 0, "ingestion-parse-" + messageId,
                ReportState.PARSED, "system.ingestion", List.of());
        steps.add("HL7_PARSED");

        boolean cdaCalled = false;
        boolean normalizerCalled = false;
        boolean converterCalled = false;
        boolean passthrough = source.passthrough();
        ClinicalDocumentResponse storedDocument = null;
        byte[] content = parsed.document();

        if (source.createCda()) {
            cdaCalled = true;
            cdaBuilder.build(new CdaBuildRequest(reportId, documentType,
                    Map.of("sourceSystemCode", source.code(), "messageType", parsed.messageType())));
            steps.add("CDA_BUILDER_CALLED_MOCK");
        }
        if (content != null && content.length > 0) {
            DocumentProcessingRequest request = new DocumentProcessingRequest(reportId, content,
                    parsed.documentFilename(), source.code(), correlationId);
            if (passthrough) {
                steps.add("PASSTHROUGH_APPLIED");
            } else {
                DocumentProcessingResult normalized = normalizer.normalize(request);
                content = normalized.content(); normalizerCalled = true;
                steps.add("DOCUMENT_NORMALIZER_CALLED_MOCK");
                request = new DocumentProcessingRequest(reportId, content, parsed.documentFilename(), source.code(), correlationId);
            }
            if (source.pdfA3Conversion()) {
                DocumentProcessingResult converted = pdfA3Converter.convert(request);
                content = converted.content(); converterCalled = true;
                steps.add("PDF_A3_CONVERTER_CALLED_MOCK");
            }
            storedDocument = documents.upload(reportId, parsed.documentFilename(), content, "system.ingestion");
            steps.add("DOCUMENT_ASSOCIATED");
        }

        ReportState finalState;
        if (!missing.isEmpty()) finalState = ReportState.INCOMPLETE;
        else if (signerId == null) {
            missing.add("SIGNER"); finalState = ReportState.MISSING_SIGNER;
        } else finalState = ReportState.READY_TO_SIGN;
        workflow.transitionForIngestion(reportId, 1, "ingestion-complete-" + messageId,
                finalState, "system.ingestion", missing);
        steps.add("REPORT_" + finalState.name());
        return new PipelineResult(reportId, storedDocument == null ? null : storedDocument.id(), finalState,
                cdaCalled, normalizerCalled, converterCalled, passthrough, List.copyOf(steps));
    }

    private List<String> missingFields(ParsedHl7Message parsed) {
        List<String> missing = new ArrayList<>();
        addMissing(missing, "PATIENT_IDENTIFIER", parsed.patientIdentifier());
        if (text(parsed.patientFirstName()) == null || text(parsed.patientLastName()) == null) missing.add("PATIENT_NAME");
        addMissing(missing, "PATIENT_FISCAL_CODE", parsed.patientFiscalCode());
        addMissing(missing, "EPISODE", parsed.episodeIdentifier());
        addMissing(missing, "REPORT_IDENTIFIER", parsed.externalReportIdentifier());
        addMissing(missing, "DOCUMENT_TYPE", parsed.documentType());
        addMissing(missing, "DEPARTMENT", parsed.department());
        if (parsed.producedAt() == null) missing.add("PRODUCED_AT");
        if (parsed.document() == null || parsed.document().length == 0) missing.add("ACTIVE_DOCUMENT");
        return missing;
    }

    private String normalizeDocumentType(String value, List<String> missing) {
        String code = text(value);
        if (code != null) code = code.toUpperCase(Locale.ROOT);
        if (code == null || !repository.documentTypeExists(code)) {
            if (!missing.contains("DOCUMENT_TYPE")) missing.add("DOCUMENT_TYPE");
            return "REF";
        }
        return code;
    }

    private void addMissing(List<String> missing, String field, String value) {
        if (text(value) == null) missing.add(field);
    }

    private String internalIdentifier(String source, String control, UUID messageId) {
        String value = "HL7-" + safe(source) + "-" + (text(control) == null ? messageId.toString() : safe(control));
        return value.substring(0, Math.min(value.length(), 80));
    }

    private String identifier(String prefix, String value, UUID messageId) {
        String result = text(value) == null ? prefix + "-MISSING-" + messageId : value.trim();
        return result.substring(0, Math.min(result.length(), 80));
    }

    private String safe(String value) { return fallback(value).replaceAll("[^A-Za-z0-9_-]", "-"); }
    private String fallback(String value) { return text(value) == null ? "" : value.trim(); }
    private String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    record PipelineResult(UUID reportId, UUID documentId, ReportState state, boolean cdaCalled,
                          boolean normalizerCalled, boolean converterCalled, boolean passthrough,
                          List<String> steps) {}
}
