# Current Status

Last verified: 2026-08-11 Europe/Rome.

## Repository State

Branch: `chore/signflow-local-test-environment`.

The repository currently contains a technical foundation for SignFlow:

- Spring Boot backend.
- Next.js frontend.
- PostgreSQL and Flyway setup.
- Docker Compose local stack.
- PowerShell helper scripts.
- Existing architecture and development notes.

## Implemented

- Backend package boundaries: `audit`, `configuration`, `identity`, `reports`, `shared`, `signatures`, `sourcesystems`, `technicalconfig`.
- `GET /api/system/info`, returning application name, version, and `UP` status.
- Global API error response support.
- Actuator health endpoint.
- Flyway migration `V1__create_application_metadata.sql`.
- Frontend shell with navigation.
- Frontend home page.
- Frontend system status page.
- Active signer portal under `/firma`, active administration pages for `Referti` and `Configurazione`, and a placeholder for `Monitoraggio`.
- Docker Compose services for PostgreSQL, Keycloak, private MinIO object storage, backend, and frontend.
- Docker Compose service for local Keycloak OIDC.
- Local Keycloak realm import with `demo.admin`, `demo.signer`, and `demo.approver` users.
- Backend Spring Security resource-server protection for `/api/**`.
- Backend role mapping for distinct `ADMINISTRATOR`, `SIGNER`, and `APPROVER` Keycloak realm roles.
- Backend `/api/auth/me` endpoint for current authenticated user details.
- JSON 401 and 403 API error responses.
- Frontend NextAuth OIDC login/logout through Keycloak.
- Frontend route protection with redirect to `/login`.
- E2E authentication test covering protected route, login, current user display, logout, and blocked access after logout.
- PostgreSQL organizational model for partitions, companies, roles, groups, application users, user-role assignments, and user-group assignments.
- Demo application users aligned with the local Keycloak identities `demo.admin`, `demo.signer`, and `demo.approver`.
- Admin APIs for user search, detail, create, update, activation, deactivation, and organization option lists.
- Admin CRUD-style APIs for creating, editing, activating, and deactivating partitions, companies, and groups.
- Persisted admin UI-text configuration for menu entries and button translations.
- Admin frontend page `/configurazione` for user search/editing, role/group/partition/company assignment, organization management, signer fiscal code, prepared counter-signer field, and UI text configuration.
- PostgreSQL models and complete admin CRUD APIs for source systems, signature providers, signature accounts, and FSE facility mappings.
- Source-system pipeline flags with explicit rejection of simultaneous CDA creation and passthrough.
- Non-secret signature-provider authentication configuration and external credential references; provider passwords are not stored.
- Technical configuration UI with demo data and configurable action/tab texts.
- Practice container, Report aggregate, minimized PatientMetadata, complete report state enum, technical flags, and fictitious demo records.
- Paginated admin report search/detail APIs with exact-ID precedence and filters for patient, signer, signer fiscal code, state, source system, department, and all report date intervals.
- Active `/referti` administration page with advanced filters, pagination, detail consultation, and configurable action texts.
- `ClinicalDocument` metadata with Report association, SHA-256, effective PDF validation, MIME type, size, version, original filename, opaque object identifier, upload author/date, active/deleted state, and logical deletion.
- PDF binaries stored only in a private MinIO bucket; PostgreSQL contains no binary document column.
- Authorized PDF upload, inline/attachment download, presigned preview URL with configurable expiry, filename hardening, and configurable size limit.
- Fictitious demo PDF materialized idempotently in MinIO for the local profile.
- `/referti` document UI for upload, metadata/version consultation, preview, download, temporary URL copying, deleted-document visibility, and logical deletion.
- Signer-only APIs for home counters, authorized Report search/detail, document list/preview/download, state legend, and profile.
- Report visibility by direct signer assignment, active group membership, or partition authorization; inaccessible Reports are not disclosed.
- Signer portal pages for home, simple/advanced search, Report detail, PDF viewer, state legend, information, profile, and logout.
- Workflow-aware transition from `READY_TO_SIGN` to `PREVIEWED` when the signer successfully opens a PDF.
- Explicit `Report` state machine with a dedicated application workflow service and no generic state-update API.
- Signer assignment/removal, missing-signer and incomplete-precondition detection, and controlled promotion to `READY_TO_SIGN`.
- Optimistic `workflow_version`, idempotency keys, append-only workflow history, and first-preview timestamp.
- PostgreSQL trigger guard that rejects state or signer changes outside the workflow persistence path.
- Reason-required administrative corrections limited to recoverable pre-signing states and validated against current preconditions.
- Admin `/referti` workflow UI for assignment, readiness, correction, missing-field diagnostics, version, first preview, and transition history.
- Persisted workflow action texts in the admin translation profile and fully fictitious workflow/document fixtures.
- Dedicated application review service for request, document view, approval, reason-required rejection, controlled return, and counter-signature preparation; state changes delegate to `ReportWorkflowService`.
- Assigned approver distinct from signer, configurable separation from producer/uploader, optimistic version protection, idempotency, and append-only decision timeline.
- Approver-only `/api/approver/**` APIs and `/approvazioni` UI with queue, PDF viewer, decisions, and explicit preview-review-signature stepper.
- Admin review forms for approver assignment, separation rules, return reason, and counter-signature preparation without real digital signature execution.
- Explicit UI states for loading, empty results, API failure, expired session, unavailable document, and incomplete Report.
- Role-aware navigation and authenticated read-only access to administrator-configured labels/translations.
- Responsive signer layout with page-width containment and horizontally scrollable Report table on narrow screens.
- Regression test plan in `docs/test-plan.md`.

## Not Yet Implemented

- Signature batch and signature attempt model.
- Signature execution and signature-provider workflow (the signer list/detail/preview portal is implemented).
- HL7 ingestion, parsing, monitoring, and raw payload storage.
- Audit event persistence.
- Analytics event persistence and publication interfaces.
- FSE 2.0 validation/submission.
- Digital preservation packaging/submission.
- ClickHouse, OpenSearch, Kafka/RabbitMQ, Superset, or Knowage.
- Production identity-provider hardening and real organization user provisioning.
- Signature-provider authentication, which remains a separate future concern.

## Baseline Commands

### Full Script

Command:

```powershell
.\scripts\test-all.ps1
```

Result: pass on 2026-08-11.

Evidence:

- Backend: 43 tests, 0 failures, 0 errors, 0 skipped.
- Frontend: `npm ci`, ESLint, type validation, and production build passed.
- Completed at 2026-08-11T11:09:44+02:00 for backend, followed by a successful frontend build.

### Backend

Command:

```powershell
.\scripts\test-backend.ps1
```

Result: pass.

Evidence:

- Maven build success.
- Tests run: 43.
- Failures: 0.
- Errors: 0.
- Skipped: 0.
- Finished at: 2026-08-11T11:09:44+02:00.

Notes:

- Testcontainers started PostgreSQL 16-alpine successfully through Docker Desktop.
- Java emitted Mockito dynamic-agent warnings; these are warnings, not test failures.

### Frontend

Command:

```powershell
.\scripts\test-frontend.ps1
```

Result: pass.

Evidence:

- `npm ci` completed.
- `npm run lint` passed.
- `npm run build` passed.
- Next.js generated 17 application pages plus the approver backend proxy route.

Notes:

- npm audit reported 6 high-severity dependency findings; no automatic dependency upgrade was included in this feature scope.
- No automated frontend unit tests are currently defined.
- Last successful lint/build run completed at 2026-08-11 11:10 Europe/Rome.

### Docker Compose Authentication Flow

Command:

```powershell
.\scripts\start.ps1
```

Result: images rebuilt successfully and all services reached healthy/running state.

Evidence:

- PostgreSQL healthy on `127.0.0.1:5432`.
- Keycloak running on `127.0.0.1:8081`.
- Backend healthy on `127.0.0.1:18080` in this local environment.
- Frontend running on `127.0.0.1:3000`.
- MinIO API and console healthy on `127.0.0.1:9000` and `127.0.0.1:9001` with a private clinical-document bucket.
- Keycloak log confirms realm `signflow` imported.
- Flyway validated 14 migrations and applied V14 successfully to the existing local database.
- Browser-integrated checks confirmed no horizontal page overflow at 1440 px or 390 x 844, a three-column/one-column responsive review stepper, controlled PDF viewer, review timeline, and no JavaScript console errors.
- The local demo PDF metadata hash matches the 613-byte object stored in MinIO; an unsigned direct object request returns 403.

API spot checks:

- `GET http://localhost:18080/api/auth/me` without token returned 401.
- `GET http://localhost:18080/api/system/info` without token returned 401.

### E2E Authentication

Command:

```powershell
.\scripts\test-e2e.ps1
```

Equivalent command used during verification:

```powershell
cd frontend
npm run e2e
```

Result: pass.

Evidence:

- Playwright ran 3 Chromium tests sequentially against the shared demo database.
- The authenticated admin visited `/configurazione` and saw users, organizational management, all four technical configuration areas, validation feedback, and the UI-text profile.
- The authenticated admin visited `/referti`, saw the fictitious records, performed an exact internal-ID lookup, verified descriptive-filter disabling, and opened the Practice/Report detail.
- The authenticated admin uploaded a PDF, saw the versioned metadata, opened the expiring presigned preview, and downloaded the original filename.
- The administrator assigned/removed the signer, uploaded a fictitious PDF, evaluated readiness to `READY_TO_SIGN`, observed the successful workflow POST, and verified configurable workflow button texts.
- The signer opened a controlled PDF, observed `PREVIEWED`, and exercised the portal at a 390 x 844 viewport without page overflow or JavaScript errors.
- The signer requested review, the approver recorded document view and approved to `APPROVED`, and the administrator restored the fictitious demo through controlled returns.
- The test verifies protected route redirect, Keycloak login, current user display, system page access with session, logout, and protected route redirect after logout.
- Last successful run completed at 2026-08-11 Europe/Rome: 3 passed, 0 failed.

## Baseline Interpretation

The build/test baseline proves that the current foundation is runnable, authentication works locally through Keycloak/OIDC, protected APIs reject unauthorized requests, and every implemented Report state/signer change passes through the versioned, idempotent workflow service with test-verifiable history. Real signature execution and document transformation workflows remain pending.
