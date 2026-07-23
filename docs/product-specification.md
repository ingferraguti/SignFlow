# Product Specification

## Purpose

SignFlow, also referred to in the source material as H-Sign or Clinical Signing Hub, is intended to become an open source middleware for healthcare organizations that need a single controlled point for remote digital signature workflows on clinical documents.

The product receives clinical reports and related artifacts from smaller clinical applications, specialist records, legacy systems, APIs, or HL7 flows; normalizes metadata; presents the work queue to signers; coordinates single and batch signing through external providers; records each relevant transition; and prepares future integrations with FSE 2.0 and digital preservation.

The current repository implements administrative identity, organization, technical pipeline configuration, Practice/Report consultation, and private PDF document management. Signer workflows and document-processing transformations remain future work.

## Scope Principles

- The product is a signing and document-governance hub, not a replacement for LIS, RIS, EHR, DSE, or a complete clinical repository.
- The first MVP must be useful as a Signing Hub and Document Gateway before adding advanced repository, privacy, analytics, or search layers.
- Pipeline behavior must be configured by `SourceSystem` and FSE mappings, not hard-coded for individual upstream systems.
- Signature providers are external integrations hidden behind adapters.
- FSE 2.0 and digital preservation are integrations prepared by SignFlow; SignFlow is not itself an FSE gateway authority or an accredited preservation service.

## Current Implementation Evidence

Implemented today:

- Modular monolith backend structure under `it.signflow`.
- Backend `GET /api/system/info`.
- Spring Boot Actuator health endpoint.
- Versioned Flyway schema for identity, organization, technical configuration, practices, reports, patient metadata, and clinical-document metadata.
- Frontend app shell with authenticated system, configuration, and report-consultation pages.
- Administrative APIs and UI for users, organizations, source systems, signature providers/accounts, FSE facility mappings, and report search/detail.
- Exact report-identifier precedence plus descriptive and date-range filters.
- Private MinIO storage with PDF upload/download/preview, SHA-256, versioning, logical deletion, and expiring URLs.
- Docker Compose services for PostgreSQL, Keycloak, MinIO, backend, and frontend.

Not implemented today:

- Signature batches, HL7 messages, audit, and analytics entities.
- Business REST APIs for signer workflows.
- Real or mock signature workflow.
- HL7 intake, CDA2 generation, PDF/A conversion, FSE, preservation, analytics, or audit persistence.

## Domain Model

The technical model must support the entities defined in `docs/domain-glossary.md`. The central aggregate for the MVP is `Report`/`Referto`, which represents the clinical signing workflow around a clinical document, patient metadata, signer, source system, technical state, and integration outcomes.

The repository uses `Practice` as an operational case/container that groups one or more `Report` records. It does not replace or rename `Report`/`Referto`, which remains the central workflow aggregate.

`ClinicalDocument` is separate from `Report`: PostgreSQL stores MIME type, size, SHA-256, version, original filename, generated object identifier, uploader, timestamps, and state; the binary is stored only in the private S3-compatible bucket. Upload accepts validated PDFs within a configurable limit, and access uses an authorized stream or a time-limited presigned URL.

## Report State Machine

The user-facing states are:

- Non firmato
- Firmato
- In errore
- Incompleto

The internal workflow states to support are:

| State | Meaning |
| --- | --- |
| `RECEIVED` | Report received through HL7, API, or another intake path. |
| `PARSED` | Metadata extracted from the inbound payload. |
| `MISSING_SIGNER` | Signer missing or not recognized. |
| `INCOMPLETE` | Required data missing, such as patient, signer, document, type, department, encounter, or fiscal code. |
| `READY_TO_SIGN` | Document ready for signature. |
| `PREVIEWED` | Document viewed by the signer at least once. |
| `SIGN_BATCH_CREATED` | Report included in a signature batch. |
| `SIGNING` | Signature operation in progress. |
| `SIGNED` | Signature completed. |
| `SIGN_ERROR` | Signature failed. |
| `FSE_VALIDATION_ERROR` | Signed document invalid for FSE 2.0. |
| `FSE_SENT` | Document submitted to the FSE gateway. |
| `FSE_ACCEPTED` | Document accepted by FSE. |
| `FSE_REJECTED` | Document rejected by FSE. |
| `CONSERVATION_SENT` | Document sent to preservation. |
| `CONSERVATION_ACCEPTED` | Document accepted by the preservation provider. |

Every state transition must eventually create append-only audit and analytics events.

## Source-System Pipeline

`SourceSystem` controls document treatment. Required configuration flags:

| Flag | Intended behavior |
| --- | --- |
| `createCda` | Build CDA2 when the inbound flow does not already contain a complete CDA. |
| `passthrough` | Accept a document already produced upstream without reconstructing it. |
| `pdfA3Conversion` | Enable PDF/A3 conversion before downstream submission. |
| `visibleSignature` | Configure visible signature placement or provider behavior. |
| `multipleSignature` | Enable multi-signature workflow rules. |
| `sendUnsigned` | Allow explicitly configured unsigned submission paths. |

Contradictory combinations, such as `createCda = true` and `passthrough = true`, must be rejected or clearly warned before activation.

## Signer Application

The signer-facing application must provide:

- Report list with simple and advanced search.
- Selectable table of assigned reports.
- Report detail and controlled PDF preview.
- Single report signature.
- Batch signature creation from selected reports or "sign all" filtered results.
- Batch review before execution.
- Temporary provider authentication/session flow.
- Per-document result display after batch execution.

Authorization rule: a signer can only see reports assigned to that signer.

## Admin Application

The admin application must provide:

- User management, including search, create/update/delete, activation, roles, groups, SAC import placeholder, Excel import placeholder, export, and counter-signer fiscal-code field.
- Admin report search with simple and advanced filters.
- Report detail with state, signer, source system, technical flags, timeline, errors, signature attempts, FSE outcomes, and conservation outcomes.
- Source system CRUD with all pipeline flags and validation.
- FSE facility mapping CRUD for facilities, companies, operating units, source systems, departments, and active state.
- Monitoring pages for processed and discarded input/output messages.
- Operational queue for reports without signer.

Authorization rule: admins can only access records allowed by their partition and role.

## API Surface

Signer APIs to implement:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/reports` | Filtered list of reports visible to the signer. |
| `GET` | `/api/reports/{id}` | Report detail. |
| `GET` | `/api/reports/{id}/preview` | Temporary URL or controlled PDF stream. |
| `POST` | `/api/reports/{id}/sign` | Single signature. |
| `POST` | `/api/signature-batches` | Create batch from selection or filters. |
| `GET` | `/api/signature-batches/{id}` | Batch detail and included reports. |
| `POST` | `/api/signature-batches/{id}/confirm` | Confirm reviewed batch. |
| `POST` | `/api/signature-batches/{id}/sign` | Start batch signature. |
| `GET` | `/api/signature-batches/{id}/result` | Batch outcome. |
| `GET` | `/api/signature-providers` | Available providers for the user. |
| `POST` | `/api/signature-providers/{id}/session` | Temporary provider session. |

Admin APIs to implement:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET/POST` | `/api/admin/users` | Search/create users. |
| `GET/PUT/DELETE` | `/api/admin/users/{id}` | User detail/update/delete. |
| `POST` | `/api/admin/users/import-sac` | Import users from SAC integration placeholder. |
| `POST` | `/api/admin/users/import-excel` | Import users from Excel. |
| `GET` | `/api/admin/users/export` | Export users. |
| `GET` | `/api/admin/reports` | Admin report search. |
| `GET` | `/api/admin/reports/{id}` | Admin report detail. |
| `GET/POST` | `/api/admin/source-systems` | Search/create source systems. |
| `GET/PUT/DELETE` | `/api/admin/source-systems/{id}` | Source system detail/update/delete. |
| `GET` | `/api/admin/source-systems/export` | Export source systems. |
| `GET/POST` | `/api/admin/fse-facility-mappings` | Search/create FSE facility mappings. |
| `GET/PUT/DELETE` | `/api/admin/fse-facility-mappings/{id}` | FSE mapping detail/update/delete. |
| `GET` | `/api/admin/monitoring/messages` | Monitoring search. |
| `GET` | `/api/admin/monitoring/messages/{id}` | Monitoring detail. |
| `GET` | `/api/admin/monitoring/reports-without-signer` | Reports blocked by missing signer. |

All list endpoints require pagination, validated filters, validated ordering, authorization, and audit where relevant.

## Storage and Events

PostgreSQL stores transactional metadata and current state:

- Users, roles, groups, partitions, companies.
- Source systems and FSE mappings.
- Reports and patient metadata references.
- Signature providers, accounts, batches, attempts.
- HL7 message metadata.
- Audit events and initial analytics events.

Object storage stores heavy payloads:

- Raw HL7.
- CDA2 and XML.
- PDF/PDF-A/PDF-A3.
- Signed documents.
- FSE receipts.
- Preservation packages and receipts.

Events must include:

- `REPORT_RECEIVED`
- `REPORT_PARSED`
- `REPORT_READY_TO_SIGN`
- `REPORT_PREVIEWED`
- `SIGNATURE_BATCH_CREATED`
- `SIGNATURE_REQUESTED`
- `REPORT_SIGNED`
- `SIGNATURE_FAILED`
- `FSE_SENT`
- `FSE_ACCEPTED`
- `CONSERVATION_SENT`
- `ERROR`

Analytics targets such as ClickHouse and troubleshooting targets such as OpenSearch are future integrations. The MVP should first stabilize event shape and persistence.

## Security and Privacy

- Separate SignFlow authentication from provider authentication.
- Never persist provider passwords.
- Use temporary provider sessions.
- Minimize patient data.
- Mask fiscal codes, patient identifiers, raw HL7 payloads, and clinical data in logs and admin troubleshooting views.
- Audit report searches, previews, signatures, configuration changes, user imports, exports, FSE submission, preservation submission, and errors.
- Enforce pagination and rate limits on search APIs.
- Use controlled streams or temporary URLs for document previews.
- Keep raw HL7 retention configurable.

## MVP Boundary

In MVP:

- Technical foundation.
- Domain model and PostgreSQL schema.
- Mock signer workflows.
- Mock provider adapter.
- Admin CRUD for users, source systems, and FSE mappings.
- Report search and detail using mock or seeded data as needed.
- HL7 ingestion mock sufficient to exercise the pipeline.
- Audit/event persistence in PostgreSQL.
- Operational monitoring pages with masked payloads.

Out of MVP:

- Full clinical repository.
- Advanced privacy/access layer with consent, blackout, emergency access, and full clinical consultation.
- Real integrations with every signature provider.
- Real FSE submission and accreditation.
- Real preservation-provider integration.
- ClickHouse, Superset/Knowage, OpenSearch, Kafka, and production-scale analytics before event stabilization.
