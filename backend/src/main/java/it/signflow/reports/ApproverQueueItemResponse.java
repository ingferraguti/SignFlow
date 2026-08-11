package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApproverQueueItemResponse(UUID id, String internalIdentifier, String patientName,
                                        String documentType, String department, OffsetDateTime producedAt,
                                        ReportState state, long workflowVersion) {
}
