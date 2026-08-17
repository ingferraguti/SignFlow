# SignFlow

SignFlow is an open source healthcare middleware for remote digital signature workflows for clinical documents. The repository includes authenticated administration, practice/report consultation, private S3-compatible clinical-document storage, PostgreSQL/Flyway, and developer tooling.

## Current status

Implemented in this phase:

- Modular monolith repository structure.
- Backend `GET /api/system/info` endpoint and Actuator health endpoint.
- Initial Flyway migration for the technical `application_metadata` table.
- Next.js administrative shell with home, system status, and placeholder areas.
- Docker Compose for PostgreSQL, Keycloak, MinIO, backend, and frontend.
- Administrative Practice/Report search plus PDF upload, preview, download, hashing, versioning, and logical deletion.

Implemented for local/MVP testing: REST and local MLLP HL7 v2 intake, HAPI parsing for fictional ORU/MDM messages,
private raw-message storage, SourceSystem-driven mock document processing, and administrator monitoring.

Also implemented for local testing: provider-neutral FSE 2.0 and conservation adapters, EU DSS preliminary validation,
mock submission/reconciliation, bounded retry, private mock receipts, complete audit history, and the administrator
page at `/integrazioni`. No healthcare or preservation endpoint is contacted.

Not yet implemented: production HL7 routing, CDA2 clinical generation, certified PDF/A-3 conversion, real
signature-provider execution, accredited FSE 2.0 transport, accredited digital preservation, Kafka, ClickHouse,
OpenSearch, and separated microservices.

## Prerequisites

- Docker and Docker Compose.
- Java 21 and Maven for local backend execution.
- Node.js 22 and npm for local frontend execution.

## Start with Docker Compose

```bash
cp .env.example .env
make up
```

If local antivirus or a corporate proxy performs TLS inspection, provide its public root certificate as a BuildKit secret instead of disabling certificate verification. See `docs/development.md` for the local override pattern.

Backend: <http://localhost:8080>
Frontend: <http://localhost:3000>
Keycloak: <http://localhost:8081>
MinIO API: <http://localhost:9000>
MinIO console: <http://localhost:9001>
Swagger UI: <http://localhost:8080/swagger-ui.html>

All published ports bind to `127.0.0.1` by default. PostgreSQL and the unauthenticated technical API are therefore not reachable from the local network.

## Local authentication

Docker Compose starts a local Keycloak realm named `signflow`. The demo identities are intentionally local-only and must not be reused in production:

```text
Administrator: demo.admin / local-admin-password
Signer:        demo.signer / local-signer-password
```

SignFlow uses Keycloak/OIDC for application login and logout. This is separate from future remote signature-provider authentication, which must remain provider-specific and temporary.

## Manual startup

Start PostgreSQL with Docker or another local instance, then run:

```bash
cd backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

In another shell:

```bash
cd frontend
npm install
NEXT_PUBLIC_BACKEND_URL=http://localhost:8080 npm run dev
```

## Windows and PowerShell

Docker Desktop with the WSL2 backend is the recommended Windows setup. PowerShell shortcuts are available under `scripts`:

```powershell
.\scripts\start.ps1
.\scripts\logs.ps1
.\scripts\test-all.ps1
.\scripts\test-e2e.ps1
.\scripts\stop.ps1
```

Additional commands include `migrate.ps1`, `test-backend.ps1`, `test-frontend.ps1`, `load-demo.ps1`, `clean.ps1`, and `reset-database.ps1`. Database reset is destructive and therefore requires typing `RESET`. If port 8080 is occupied, set `BACKEND_PORT=18080`, `NEXT_PUBLIC_BACKEND_URL=http://localhost:18080`, and keep `BACKEND_INTERNAL_URL=http://backend:8080` in the ignored local `.env` before building the frontend.

## Environment variables

See `.env.example`. Local credentials are placeholders and must be changed for non-local use. Do not commit real secrets.

## Tests and checks

```bash
make backend-test
make frontend-test
make lint
make test
```

Run the E2E authentication test after the Docker Compose stack is up:

```powershell
.\scripts\test-e2e.ps1
```

## Repository structure

```text
backend/   Spring Boot modular monolith backend
frontend/  Next.js App Router frontend
infra/     Reserved for future local infrastructure notes and scripts
docs/      Architecture and development documentation
```
