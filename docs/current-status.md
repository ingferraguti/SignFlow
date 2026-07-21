# Current Status

Last verified: 2026-07-21 11:58 Europe/Rome.

## Repository State

Branch: `chore/signflow-local-test-environment`.

The repository currently contains a technical foundation for SignFlow:

- Spring Boot backend.
- Next.js frontend.
- PostgreSQL and Flyway setup.
- Docker Compose local stack.
- PowerShell helper scripts.
- Existing architecture and development notes.

There are local uncommitted changes outside this documentation goal. They were already present when this status was recorded and were not reverted.

## Implemented

- Backend package boundaries: `audit`, `configuration`, `identity`, `reports`, `shared`, `signatures`, `sourcesystems`.
- `GET /api/system/info`, returning application name, version, and `UP` status.
- Global API error response support.
- Actuator health endpoint.
- Flyway migration `V1__create_application_metadata.sql`.
- Frontend shell with navigation.
- Frontend home page.
- Frontend system status page.
- Placeholder frontend pages for `Referti`, `Firma`, `Monitoraggio`, and `Configurazione`.
- Docker Compose services for PostgreSQL, backend, and frontend.

## Not Yet Implemented

- Report/referto domain entity and database table.
- Patient metadata model.
- Clinical document metadata model and object-storage integration.
- User, role, group, partition, company management.
- Signature provider, signature account, signature batch, and signature attempt model.
- Signer report list/detail/preview/signature workflow.
- Admin CRUD APIs and pages.
- Source-system configuration and pipeline flags.
- FSE facility mappings.
- HL7 ingestion, parsing, monitoring, and raw payload storage.
- Audit event persistence.
- Analytics event persistence and publication interfaces.
- FSE 2.0 validation/submission.
- Digital preservation packaging/submission.
- Real authentication and authorization.
- ClickHouse, OpenSearch, Kafka/RabbitMQ, MinIO, Superset, or Knowage.

## Baseline Commands

### Full Script

Command:

```powershell
.\scripts\test-all.ps1
```

Result: timed out after about 124 seconds without useful command output in this Codex run. Backend and frontend were then executed separately.

### Backend

Command:

```powershell
.\scripts\test-backend.ps1
```

Result: pass.

Evidence:

- Maven build success.
- Tests run: 3.
- Failures: 0.
- Errors: 0.
- Skipped: 0.
- Finished at: 2026-07-21T11:58:32+02:00.

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
- Next.js generated 9 static pages.

Notes:

- npm audit reported 2 moderate severity vulnerabilities.
- No automated frontend unit tests are currently defined.

## Baseline Interpretation

The build/test baseline proves that the current technical foundation is runnable and that the existing system endpoint and frontend shell compile. It does not prove any business workflow because no business workflow is implemented yet.
