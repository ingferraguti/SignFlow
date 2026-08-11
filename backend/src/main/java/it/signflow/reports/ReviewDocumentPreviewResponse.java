package it.signflow.reports;

import java.time.OffsetDateTime;

public record ReviewDocumentPreviewResponse(String url, OffsetDateTime expiresAt, ReportState state,
                                            long workflowVersion, ReportReviewOverviewResponse review) {
}
