package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewDecisionResponse(
        UUID id, UUID reportId, String operationKey, ReviewDecisionType decisionType,
        String actorUsername, String actorRole, String reason, ReportState fromState,
        ReportState toState, long previousVersion, long resultingVersion, OffsetDateTime createdAt) {
}
