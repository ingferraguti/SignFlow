package it.signflow.fse;

public record AdapterResult(
        Status status,
        String remoteReference,
        byte[] receipt,
        String receiptType,
        String mimeType,
        String errorCode,
        String errorMessage) {
    public enum Status { VALID, PENDING, ACCEPTED, REJECTED, TIMEOUT }
}
