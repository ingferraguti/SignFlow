# Goals 8-9 - FSE 2.0 and Digital Preservation Adapters

## Objective

Exercise the post-signature delivery workflow locally without contacting healthcare or preservation endpoints, while keeping provider contracts replaceable and every outcome reconstructable.

## Implemented Scope

- Provider-neutral `FseGatewayAdapter` and `ConservationAdapter` contracts for submission and reconciliation.
- EU DSS 6.4 preliminary PDF/PAdES validator; mock signatures are admitted only by the explicit local test gate.
- Facility, operating-unit, department, source-system, and document-type metadata built from the active FSE facility mapping.
- Versioned delivery operations, append-only attempts, idempotent commands, correlation IDs, bounded retry, timeout, and reconciliation.
- Report transitions through `ReportWorkflowService` for FSE validation error, sent, accepted/rejected and conservation sent, accepted/rejected.
- Mock receipts explicitly marked `mockOnly=true` and `legalValue=false`, stored in private object storage with PostgreSQL hash/metadata only.
- Database-triggered audit events for operation creation/state, every attempt, and every stored receipt.
- Administrator-only `/integrazioni` page with filters, pagination, operation detail, attempts, errors, retry, reconciliation, and receipt download.
- Two entirely fictional demo Reports: one accepted through FSE and preservation, one rejected by FSE and available for controlled retry.

## Safety Boundary

No real FSE or preservation URL, credential, JWT, patient payload, or provider-specific assumption is configured. Enabling a real adapter requires authoritative documentation, an explicit test environment, credentials, trust material, privacy review, and accredited conformance evidence.

## Verification

```powershell
.\scripts\test-backend.ps1
.\scripts\test-frontend.ps1
.\scripts\test-e2e.ps1
.\scripts\test-all.ps1
```

The focused backend suite is `ExternalDeliveryWorkflowIntegrationTest`. It covers the full accepted flow, stored receipts/audit, validation error, rejection, retry, timeout, idempotency, authorization, and concurrent reconciliation.

## Deferred

- Official FSE Gateway accreditation assets and live/sandbox transport.
- Production JWT/signature profiles, regional metadata extensions, and provider credentials.
- Accredited preservation packaging and real preservation-provider submission.
