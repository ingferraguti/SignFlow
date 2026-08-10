package it.signflow.reports;

import java.time.LocalDate;

public record SignerReportSearchRequest(
        String query,
        String patient,
        String documentType,
        String department,
        String state,
        LocalDate producedFrom,
        LocalDate producedTo,
        LocalDate signedFrom,
        LocalDate signedTo,
        int page,
        int size) {
}
