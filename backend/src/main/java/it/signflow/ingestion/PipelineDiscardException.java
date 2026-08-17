package it.signflow.ingestion;

final class PipelineDiscardException extends RuntimeException {
    private final String code;
    private final java.util.UUID reportId;

    PipelineDiscardException(String code, String message) { this(code, message, null); }
    PipelineDiscardException(String code, String message, java.util.UUID reportId) {
        super(message); this.code = code; this.reportId = reportId;
    }
    String code() { return code; }
    java.util.UUID reportId() { return reportId; }
}
