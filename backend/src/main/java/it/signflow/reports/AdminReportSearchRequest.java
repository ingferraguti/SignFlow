package it.signflow.reports;

import java.time.LocalDate;
import java.util.UUID;

record AdminReportSearchRequest(
        String internalIdentifier,
        String externalIdentifier,
        String fseIdentifier,
        String patient,
        String signer,
        String signerFiscalCode,
        String state,
        UUID sourceSystemId,
        String department,
        LocalDate producedFrom,
        LocalDate producedTo,
        LocalDate modifiedFrom,
        LocalDate modifiedTo,
        LocalDate signedFrom,
        LocalDate signedTo,
        int page,
        int size,
        String sortBy,
        String direction) {
}
