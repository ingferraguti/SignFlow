# Goal 7 - Append-only Audit, History, and Monitoring

## Objective

Make every relevant SignFlow workflow operation reconstructable from an authorized application timeline without consulting raw technical logs.

## Implemented Scope

- PostgreSQL `audit_events` ledger with timestamp, event type, user or technical actor, correlation ID, entity, outcome, minimal metadata, optional reason, and retention deadline.
- Database guard that rejects every update and arbitrary delete; only the dedicated retention transaction can remove expired events.
- Database-triggered events for Report workflow/assignment, review decisions, document upload/removal, signature batches, attempts, retries, and provider outcomes.
- HTTP-boundary events for relevant login/logout, sensitive administrative searches, document opening/upload requests, and configuration changes.
- Explicit reserved event types and fictional demo entries for future FSE and preservation operations.
- Metadata allow-list behavior that accepts only small scalar values and rejects content, payloads, passwords, tokens, OTPs, fiscal/patient/clinical fields, and nested data.
- Administrator-only APIs for filtered/paginated search, authorized CSV export, Report timeline, document history, signature history, and retention management.
- `/monitoraggio` administrator UI with responsive filters, pagination, CSV export, retention form, and minimal metadata display.
- Report-detail timeline combining Report, document, review, and signature activity.
- Configurable menu, button, and section labels in the administrator text/translation profile.

## Privacy Boundary

Audit metadata never contains document bytes, provider credentials, access tokens, OTP values, complete fiscal codes, patient identity, diagnoses, or unnecessary healthcare data. Technical logs remain separate from the application audit ledger.

## Verification

```powershell
.\scripts\test-backend.ps1
.\scripts\test-frontend.ps1
.\scripts\test-e2e.ps1
.\scripts\test-all.ps1
```

The focused backend suite is `AuditIntegrationTest`. Browser verification covers administrator authorization, fictional demo entries, filters, CSV download, Report timeline, desktop/mobile containment, JavaScript console, and application API calls. The existing authentication E2E flow also covers audit search/export and mobile containment.

## Deferred

- External SIEM shipping and tamper-evident remote archival.
- Real FSE and preservation adapter operations.
- Production-scale analytics, rate limiting, and externalized retention scheduling.
