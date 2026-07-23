# Current Status

Last verified: 2026-07-23 11:29 Europe/Rome.

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
- Placeholder frontend pages for `Referti`, `Firma`, and `Monitoraggio`; active administration page for `Configurazione`.
- Docker Compose services for PostgreSQL, backend, and frontend.
- Docker Compose service for local Keycloak OIDC.
- Local Keycloak realm import with `demo.admin` and `demo.signer` users.
- Backend Spring Security resource-server protection for `/api/**`.
- Backend role mapping for `ADMINISTRATOR` and `SIGNER` from Keycloak realm roles.
- Backend `/api/auth/me` endpoint for current authenticated user details.
- JSON 401 and 403 API error responses.
- Frontend NextAuth OIDC login/logout through Keycloak.
- Frontend route protection with redirect to `/login`.
- E2E authentication test covering protected route, login, current user display, logout, and blocked access after logout.
- PostgreSQL organizational model for partitions, companies, roles, groups, application users, user-role assignments, and user-group assignments.
- Demo application users aligned with the local Keycloak identities `demo.admin` and `demo.signer`.
- Admin APIs for user search, detail, create, update, activation, deactivation, and organization option lists.
- Admin CRUD-style APIs for creating, editing, activating, and deactivating partitions, companies, and groups.
- Persisted admin UI-text configuration for menu entries and button translations.
- Admin frontend page `/configurazione` for user search/editing, role/group/partition/company assignment, organization management, signer fiscal code, prepared counter-signer field, and UI text configuration.
- PostgreSQL models and complete admin CRUD APIs for source systems, signature providers, signature accounts, and FSE facility mappings.
- Source-system pipeline flags with explicit rejection of simultaneous CDA creation and passthrough.
- Non-secret signature-provider authentication configuration and external credential references; provider passwords are not stored.
- Technical configuration UI with demo data and configurable action/tab texts.
- Regression test plan in `docs/test-plan.md`.

## Not Yet Implemented

- Report/referto domain entity and database table.
- Patient metadata model.
- Clinical document metadata model and object-storage integration.
- Signature batch and signature attempt model.
- Signer report list/detail/preview/signature workflow.
- HL7 ingestion, parsing, monitoring, and raw payload storage.
- Audit event persistence.
- Analytics event persistence and publication interfaces.
- FSE 2.0 validation/submission.
- Digital preservation packaging/submission.
- ClickHouse, OpenSearch, Kafka/RabbitMQ, MinIO, Superset, or Knowage.
- Production identity-provider hardening and real organization user provisioning.
- Signature-provider authentication, which remains a separate future concern.

## Baseline Commands

### Full Script

Command:

```powershell
.\scripts\test-all.ps1
```

Result: not rerun for this status update. Backend, frontend, API, Compose, and E2E checks were run separately.

### Backend

Command:

```powershell
.\scripts\test-backend.ps1
```

Result: pass.

Evidence:

- Maven build success.
- Tests run: 17.
- Failures: 0.
- Errors: 0.
- Skipped: 0.
- Finished at: 2026-07-23T11:24:58+02:00.

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
- Next.js generated 11 routes/pages.

Notes:

- npm audit reported 3 vulnerabilities: 1 moderate and 2 high.
- No automated frontend unit tests are currently defined.
- Last successful lint/build run completed at 2026-07-23 11:26 Europe/Rome.

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
- Keycloak log confirms realm `signflow` imported.

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

- Playwright ran 1 Chromium test.
- The authenticated admin visited `/configurazione` and saw users, organizational management, all four technical configuration areas, validation feedback, and the UI-text profile.
- The test verifies protected route redirect, Keycloak login, current user display, system page access with session, logout, and protected route redirect after logout.
- Last successful run completed at 2026-07-23 11:28 Europe/Rome.

## Baseline Interpretation

The build/test baseline proves that the current technical foundation is runnable, application authentication works locally through Keycloak/OIDC, protected APIs reject unauthenticated requests, the frontend login/logout path works through Docker Compose, and user, organization, and technical configuration administration are covered by backend integration and browser tests. It does not prove any clinical business workflow because those workflows are still not implemented.
