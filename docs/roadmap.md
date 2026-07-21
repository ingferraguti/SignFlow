# Roadmap

The roadmap converts the source document into non-overlapping development goals. It does not claim that later goals are in the MVP unless explicitly marked.

## Goal 0 - Technical Foundation

Status: partially implemented.

Purpose:

- Keep a runnable modular monolith.
- Keep backend/frontend separation.
- Keep PostgreSQL/Flyway and Docker Compose local development.
- Expose technical readiness endpoints and placeholder UI areas.

Acceptance:

- Backend tests pass.
- Frontend lint and production build pass.
- `GET /api/system/info` and Actuator health remain available.
- Placeholder routes clearly do not pretend business behavior exists.

## Goal 1 - Executable Specification and Traceability

Status: this documentation set.

Purpose:

- Transform the functional project document into traceable technical documentation.
- Define domain language, roadmap, acceptance matrix, current status, and goal tracking.
- Record the initial test/build baseline.

Acceptance:

- Required files exist: `AGENTS.md`, `docs/product-specification.md`, `docs/domain-glossary.md`, `docs/roadmap.md`, `docs/acceptance-matrix.md`, `docs/current-status.md`, `docs/goals/README.md`.
- Every known functional area from the source document has a mapped goal, state, dependency, acceptance criterion, and MVP boundary.
- No application behavior is changed by this goal.

## Goal 2 - Domain Model and Persistence

MVP: yes.

Purpose:

- Create PostgreSQL-backed domain entities for organizations, users, reports, source systems, documents, signature providers, batches, attempts, HL7 metadata, audit, and analytics events.

Acceptance:

- Migrations create all MVP tables with UUID identifiers and required indexes.
- `Report` state machine is represented explicitly.
- Heavy document payloads are referenced, not stored in relational columns.
- Provider passwords cannot be persisted.
- Tests cover mappings, required fields, state values, and migration startup.

Out of MVP:

- Full clinical repository behavior.
- OpenSearch and ClickHouse ingestion.

## Goal 3 - Admin Configuration

MVP: yes.

Purpose:

- Implement admin CRUD for users, groups, source systems, signature providers, and FSE facility mappings.

Acceptance:

- Admin APIs expose paginated search, create, update, delete, validation, and export where specified.
- Source-system flag combinations are validated or warned.
- User form includes role/group assignment, active state, fiscal code, and counter-signer fiscal-code field.
- Admin actions create audit events.

Out of MVP:

- Real SAC integration; only a placeholder/import contract is required.
- Complete enterprise authorization model beyond partition/role rules.

## Goal 4 - Signer Workflow With Mock Provider

MVP: yes.

Purpose:

- Provide signer report search, preview, single signature, and batch signature workflow using a mock provider adapter.

Acceptance:

- A signer can only see assigned reports.
- Report preview is audited and delivered through a controlled URL or stream.
- Single signature moves one report through signing states.
- Batch signature has global batch state and per-report outcomes.
- Partial success and failure are represented.

Out of MVP:

- Real provider integration.
- Long-running provider session hardening beyond temporary mock sessions.

## Goal 5 - Configurable Ingestion Pipeline

MVP: yes, with mock intake.

Purpose:

- Implement a mockable `ReportIngestionService` driven by `SourceSystem` configuration.

Acceptance:

- The service identifies source system configuration, extracts report/patient/signer metadata, creates HL7 metadata, creates or updates a report, applies `createCda`, `passthrough`, and `pdfA3Conversion` behavior through interfaces, and publishes audit/analytics events.
- Missing signer leads to `MISSING_SIGNER`.
- Missing mandatory metadata leads to `INCOMPLETE`.
- Valid reports become `READY_TO_SIGN`.

Out of MVP:

- Production HL7 MLLP connectivity.
- Complete CDA2 compliance and real PDF/A3 conversion.

## Goal 6 - Monitoring and Operational Queues

MVP: yes.

Purpose:

- Expose operational troubleshooting for processed/discarded messages and reports without signer.

Acceptance:

- Monitoring pages and APIs support period, source system, message type, state, correlation id, external id, and patient fiscal-code filters where available.
- Raw payload display is masked.
- Message detail shows metadata, error, and timeline.
- Reports without signer are visible as a dedicated operational queue.

Out of MVP:

- OpenSearch-backed free-text investigation.
- Full SLA dashboards.

## Goal 7 - Audit and Analytics Event Foundation

MVP: yes.

Purpose:

- Persist append-only audit events and shape analytics events for later BI.

Acceptance:

- Every report state transition and relevant user/admin action produces an audit event.
- Events avoid unnecessary sensitive data.
- Event interfaces allow future ClickHouse/OpenSearch publication.

Out of MVP:

- ClickHouse, Superset, Knowage, Kafka, and OpenSearch production integration.

## Goal 8 - FSE 2.0 Preparation

MVP: partial preparation only.

Purpose:

- Prepare metadata, validation boundaries, and integration contracts for FSE 2.0.

Acceptance:

- FSE facility mappings normalize facility/company/unit/source-system metadata.
- Report states include FSE validation, sent, accepted, and rejected outcomes.
- Interfaces isolate official validator/gateway integration.

Out of MVP:

- Real FSE submission.
- Accreditation workflow.

## Goal 9 - Digital Preservation Preparation

MVP: partial preparation only.

Purpose:

- Prepare packages, metadata, and receipts for future accredited preservation integration.

Acceptance:

- Domain model can reference preservation packages and receipts.
- Report states include conservation sent and accepted outcomes.
- Interfaces isolate preservation-provider APIs.

Out of MVP:

- Acting as an accredited preservation service.
- Real preservation-provider integration.

## Goal 10 - Advanced Repository, Privacy, and BI

MVP: no.

Purpose:

- Add full clinical repository features, advanced privacy/access layer, consent/blackout/emergency workflows, and mature BI/search.

Acceptance:

- To be defined after MVP event and domain model stabilization.

Out of MVP:

- Entire goal.
