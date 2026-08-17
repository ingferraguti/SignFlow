package it.signflow.fse;

public record DeliveryRequest(
        String contextReference,
        byte[] document,
        DeliveryMetadata metadata,
        String correlationId,
        int submissionNumber) {
}
