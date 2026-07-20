# SignFlow

SignFlow is an open source healthcare middleware for remote digital signature workflows for clinical documents. This repository currently contains only the technical foundation: a Spring Boot backend, a Next.js frontend, PostgreSQL configuration, Flyway migration support, and developer tooling.

## Current status

Implemented in this phase:

- Modular monolith repository structure.
- Backend `GET /api/system/info` endpoint and Actuator health endpoint.
- Initial Flyway migration for the technical `application_metadata` table.
- Next.js administrative shell with home, system status, and placeholder areas.
- Docker Compose for PostgreSQL, backend, and frontend.

Intentionally excluded: HL7 intake, CDA2 generation, real digital signature providers, FSE 2.0 submission, digital preservation, complete user/patient/report management, real authentication, Kafka, ClickHouse, OpenSearch, MinIO, and separated microservices.

## Prerequisites

- Docker and Docker Compose.
- Java 21 and Maven for local backend execution.
- Node.js 22 and npm for local frontend execution.

## Start with Docker Compose

```bash
cp .env.example .env
make up
```

Backend: <http://localhost:8080>
Frontend: <http://localhost:3000>
Swagger UI: <http://localhost:8080/swagger-ui.html>

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

## Environment variables

See `.env.example`. Local credentials are placeholders and must be changed for non-local use. Do not commit real secrets.

## Tests and checks

```bash
make backend-test
make frontend-test
make lint
make test
```

## Repository structure

```text
backend/   Spring Boot modular monolith backend
frontend/  Next.js App Router frontend
infra/     Reserved for future local infrastructure notes and scripts
docs/      Architecture and development documentation
```
