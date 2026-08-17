package it.signflow.fse;

public record ReconciliationRequest(
        String contextReference,
        String remoteReference,
        String correlationId,
        int submissionNumber,
        int reconciliationNumber) {
}
