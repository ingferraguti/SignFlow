package it.signflow.fse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class MockFseGatewayAdapter implements FseGatewayAdapter {
    public static final String CODE = "MOCK_FSE_2_0_TEST_ONLY";
    private final MockDeliveryScenarioResolver scenarios;
    private final ObjectMapper objectMapper;

    MockFseGatewayAdapter(MockDeliveryScenarioResolver scenarios, ObjectMapper objectMapper) {
        this.scenarios = scenarios; this.objectMapper = objectMapper;
    }

    @Override public String code() { return CODE; }

    @Override public AdapterResult validate(DeliveryRequest request) {
        return new AdapterResult(AdapterResult.Status.VALID, null, null, null, null, null, null);
    }

    @Override public AdapterResult submit(DeliveryRequest request) {
        String reference = "mock-fse-" + request.correlationId();
        return result(AdapterResult.Status.PENDING, reference, request.correlationId(), "SUBMISSION", null, null);
    }

    @Override public AdapterResult reconcile(ReconciliationRequest request) {
        AdapterResult.Status status = scenarios.outcome(request.contextReference(), "FSE",
                request.submissionNumber(), request.reconciliationNumber());
        String type = switch (status) {
            case ACCEPTED -> "ACCEPTANCE";
            case REJECTED -> "REJECTION";
            case TIMEOUT -> "TIMEOUT";
            default -> "SUBMISSION";
        };
        return result(status, request.remoteReference(), request.correlationId(), type,
                status == AdapterResult.Status.REJECTED ? "MOCK_FSE_REJECTED" :
                        status == AdapterResult.Status.TIMEOUT ? "MOCK_FSE_TIMEOUT" : null,
                status == AdapterResult.Status.REJECTED ? "Rifiuto FSE simulato" :
                        status == AdapterResult.Status.TIMEOUT ? "Timeout FSE simulato" : null);
    }

    private AdapterResult result(AdapterResult.Status status, String reference, String correlationId,
                                 String receiptType, String errorCode, String errorMessage) {
        return new AdapterResult(status, reference, receipt(status, reference, correlationId), receiptType,
                "application/json", errorCode, errorMessage);
    }

    private byte[] receipt(AdapterResult.Status status, String reference, String correlationId) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("mockOnly", true); value.put("legalValue", false); value.put("adapter", CODE);
            value.put("status", status.name()); value.put("reference", reference);
            value.put("correlationId", correlationId);
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception exception) { throw new IllegalStateException("Unable to create mock FSE receipt", exception); }
    }
}
