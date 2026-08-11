package it.signflow.reports;

public record ReviewOperationResponse(ReviewDecisionType operation, ReportState state, long version,
                                      boolean idempotent, ReportReviewOverviewResponse review) {
}
