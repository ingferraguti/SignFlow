package it.signflow.fse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ExternalDeliveryModels {
    private ExternalDeliveryModels() {}

    public enum Channel { FSE, CONSERVATION }
    public enum State {
        FSE_VALIDATION_ERROR, FSE_SENT, FSE_ACCEPTED, FSE_REJECTED,
        CONSERVATION_SENT, CONSERVATION_ACCEPTED, CONSERVATION_REJECTED, TIMEOUT
    }

    public record CommandRequest(
            long expectedVersion,
            @NotBlank @Size(max = 80) String operationKey) {
    }

    public record OperationSummary(
            UUID id, UUID reportId, String reportIdentifier, UUID documentId, Channel channel, State state,
            String correlationId, String adapterCode, String facilityCode, String facilityName,
            String operatingUnit, String department, String documentTypeCode, String remoteReference,
            int attemptCount, int maxRetries, long version, long reportVersion,
            String errorCode, String errorMessage, String createdBy, OffsetDateTime createdAt,
            OffsetDateTime sentAt, OffsetDateTime completedAt, OffsetDateTime reconciledAt,
            OffsetDateTime updatedAt) {
    }

    public record AttemptResponse(
            UUID id, int attemptNumber, String action, String outcome, String correlationId,
            String remoteReference, String errorCode, String errorMessage,
            OffsetDateTime startedAt, OffsetDateTime completedAt) {
    }

    public record ReceiptResponse(
            UUID id, UUID attemptId, String receiptType, String mimeType, long sizeBytes,
            String sha256, OffsetDateTime createdAt, String downloadUrl) {
    }

    public record OperationDetail(OperationSummary operation, DeliveryMetadata metadata,
                                  List<AttemptResponse> attempts, List<ReceiptResponse> receipts,
                                  boolean retryAllowed, boolean reconciliationAllowed) {
    }

    public record OperationPage(List<OperationSummary> items, int page, int size, long total) {
    }
}
