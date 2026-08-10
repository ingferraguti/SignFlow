package it.signflow.reports;

import java.util.List;

public record SignerHomeResponse(
        long total,
        long readyToSign,
        long reviewPending,
        long incomplete,
        long signed,
        List<ReportSummaryResponse> recentReports) {
}
