package it.signflow.fse;

import org.springframework.stereotype.Component;

@Component
public class DeferredNationalFseGatewayConnector implements NationalFseGatewayConnector {
    @Override
    public GatewayResult validate(byte[] pdfWithCda, GatewayMetadata metadata) {
        return new GatewayResult(Status.NOT_CONFIGURED, null, "National FSE Gateway adapter not configured");
    }

    @Override
    public GatewayResult publish(byte[] signedPdfWithCda, GatewayMetadata metadata, String workflowInstanceId) {
        return new GatewayResult(Status.NOT_CONFIGURED, workflowInstanceId, "National FSE Gateway adapter not configured");
    }
}
