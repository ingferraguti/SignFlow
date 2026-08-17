package it.signflow.fse;

public interface DocumentPreflightValidator {
    ValidationResult validate(byte[] document, String signatureKind);

    record ValidationResult(boolean valid, String validator, String code, String detail) {
    }
}
