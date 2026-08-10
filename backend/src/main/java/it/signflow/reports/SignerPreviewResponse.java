package it.signflow.reports;

import java.time.OffsetDateTime;

public record SignerPreviewResponse(
        String url,
        OffsetDateTime expiresAt,
        ReportState reportState,
        long workflowVersion,
        OffsetDateTime firstPreviewedAt) {
}
