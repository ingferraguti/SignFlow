package it.signflow.signatures;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

enum SignatureSelectionMode { SINGLE, MANUAL, FILTERED }
enum SignatureBatchState { DRAFT, CONFIRMED, RUNNING, COMPLETED, PARTIAL_SUCCESS, FAILED, CANCELLED }
enum SignatureAttemptState { PENDING, SIGNING, SUCCEEDED, FAILED, CANCELLED }

record ProviderSessionRequest(@NotBlank @Size(max = 40) String authorizationCode) {
}

record ProviderSessionResponse(UUID id, String providerCode, String state, OffsetDateTime expiresAt,
                               String warning) {
}

record SignatureSelectionFilter(@Size(max = 160) String query, @Size(max = 160) String patient,
                                @Size(max = 80) String documentType, @Size(max = 160) String department) {
}

record CreateSignatureBatchRequest(@NotNull SignatureSelectionMode selectionMode, List<UUID> reportIds,
                                   @Valid SignatureSelectionFilter filters,
                                   @NotBlank @Size(max = 120) String operationKey) {
}

record SignatureOperationRequest(@NotBlank @Size(max = 120) String operationKey, UUID providerSessionId) {
}

record SingleSignatureRequest(@NotNull UUID reportId, @NotNull UUID providerSessionId,
                              @NotBlank @Size(max = 120) String operationKey) {
}

record SignatureAttemptResponse(UUID id, UUID reportId, String reportIdentifier, SignatureAttemptState state,
                                int retryCount, int maxRetries, String providerReference, UUID artifactId,
                                String artifactName, String artifactNotice, String errorCode, String errorMessage,
                                OffsetDateTime startedAt, OffsetDateTime completedAt) {
}

record SignatureBatchResponse(UUID id, String signerUsername, String providerCode,
                              SignatureSelectionMode selectionMode, String filterSnapshot,
                              SignatureBatchState state, long version, int totalCount, int successCount,
                              int failureCount, OffsetDateTime createdAt, OffsetDateTime confirmedAt,
                              OffsetDateTime startedAt, OffsetDateTime completedAt, OffsetDateTime cancelledAt,
                              String warning, List<SignatureAttemptResponse> attempts) {
}

record SignatureArtifact(String filename, String content) {
}
