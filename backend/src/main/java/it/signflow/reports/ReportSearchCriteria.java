package it.signflow.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

record ReportSearchCriteria(
        ReportLookupType lookupType,
        String exactIdentifier,
        String patient,
        String signer,
        String signerFiscalCode,
        ReportState state,
        UUID sourceSystemId,
        String department,
        OffsetDateTime producedFrom,
        OffsetDateTime producedToExclusive,
        OffsetDateTime modifiedFrom,
        OffsetDateTime modifiedToExclusive,
        OffsetDateTime signedFrom,
        OffsetDateTime signedToExclusive,
        int page,
        int size,
        String sortBy,
        String direction) {
}
