# Goal 5 / Goal 6 / Delivery Objective 13 — HL7 Ingestion

## Objective

Receive completely fictional ORU/MDM HL7 v2 messages through REST or a local MLLP listener, retain the raw payload
privately, and create a consultable Report through the SourceSystem-configured document pipeline.

## In Scope

- HAPI HL7 v2 parsing and initial validation.
- Protected raw-HL7 REST endpoint and loopback-first local MLLP listener.
- `Hl7Message` processing metadata in PostgreSQL and raw payload in private object storage.
- Correlation, processed/discarded outcomes, idempotency, changed duplicate rejection, and concurrent delivery protection.
- Patient/episode/Report/signer/document extraction with privacy-minimized monitoring.
- Explicit mock/deferred `CdaBuilder`, `DocumentNormalizer`, and `PdfA3Converter` adapters.
- SourceSystem flags for CDA invocation, passthrough, and PDF/A-3 mock conversion.
- Workflow-service transitions to `INCOMPLETE`, `MISSING_SIGNER`, or `READY_TO_SIGN`.
- Administrator message monitoring, masked detail, errors, pagination, and missing-signer queue.
- Append-only receipt/outcome audit events and Report timeline linkage.

## Out of Scope

- Clinical CDA R2 generation, official schematron validation, or FSE accreditation.
- Certified PDF/A-3 conversion or any legally/clinically valid transformation claim.
- Production MLLP topology, mTLS/VPN, durable broker routing, or external master-data remediation.
- OpenSearch and analytics publication.

## Acceptance Criteria

- A fictional ORU message for `LIS-DEMO` reaches `READY_TO_SIGN` and has an associated PDF.
- Missing mandatory metadata reaches `INCOMPLETE`; missing signer reaches `MISSING_SIGNER` and the operational queue.
- Exact retries return the same message/Report and concurrent duplicates create one Report.
- Changed content reusing the same SourceSystem/control ID is discarded with a visible safe reason.
- Raw content is private and the UI/API detail contains only a masked segment-level preview.
- All state transitions are recorded through `ReportWorkflowService` and the complete flow is visible in the timeline.

## Verification Commands

```powershell
.\scripts\test-backend.ps1
.\scripts\test-frontend.ps1
.\scripts\test-e2e.ps1
.\scripts\test-all.ps1
```

## Implementation Notes

HAPI HL7 v2 is provider-neutral and limited here to local intake testing. Mock document adapters are intentionally
labelled as mock and do not fabricate CDA clinical content or claim certified PDF/A-3 conversion.
