package it.signflow.fse;

/** Outbound connector boundary. Authentication, validation and publication are implemented by future adapters. */
public interface NationalFseGatewayConnector {
    GatewayResult validate(byte[] pdfWithCda, GatewayMetadata metadata);
    GatewayResult publish(byte[] signedPdfWithCda, GatewayMetadata metadata, String workflowInstanceId);

    record GatewayMetadata(String documentTypeCode, String facilityCode, String documentIdentifier) {}
    record GatewayResult(Status status, String workflowInstanceId, String detail) {}
    enum Status { NOT_CONFIGURED, ACCEPTED, REJECTED }
}
