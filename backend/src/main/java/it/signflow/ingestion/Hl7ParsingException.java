package it.signflow.ingestion;

final class Hl7ParsingException extends RuntimeException {
    private final String code;

    Hl7ParsingException(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() { return code; }
}
