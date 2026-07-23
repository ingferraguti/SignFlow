# Acceptance Matrix

Status values:

- Implemented: available in current code.
- Partial: foundation exists, but business behavior is missing.
- Planned: documented but not implemented.
- Out of MVP: intentionally deferred.

| Feature | Development goal | Current state | Dependencies | Acceptance criterion | Out of MVP |
| --- | --- | --- | --- | --- | --- |
| Modular monolith foundation | Goal 0 | Partial | Spring Boot, Next.js, PostgreSQL, Flyway, Docker Compose | Backend and frontend build/test baseline passes; module boundaries exist. | Microservice split. |
| System status API | Goal 0 | Implemented | `SystemInfoController`, frontend system page | `GET /api/system/info` returns application name, version, status; frontend can render system status. | Business health checks. |
| Domain vocabulary and `Practice` mapping | Goal 1 / Goal 5 | Implemented | Glossary, Practice/Report schema | Practice is an operational container for one or more Reports and never replaces the central Report/Referto aggregate. | Renaming existing code indiscriminately. |
| Report/referto aggregate | Goal 2 / Goal 5 | Implemented for admin consultation | PostgreSQL schema, domain model, state machine, admin API/UI | Report stores internal/external/FSE ids, patient metadata, signer, source system, document type, department, dates, state, technical flags, and indexes; admin search/detail is available. | Complete clinical repository and binary document storage. |
| Clinical document storage model | Goal 2 | Planned | Object-storage interface, metadata tables | Binary artifacts are stored by reference with type, hash, version, and correlation metadata. | Direct relational storage of large payloads. |
| Patient metadata minimization | Goal 2 / Goal 5 | Implemented for report administration | Domain model, privacy rules | Only necessary patient metadata is stored/displayed; logs and analytics avoid sensitive payloads. | Advanced consent/blackout management. |
| Users, roles, groups, partitions, companies | Goal 2 / Goal 3 | Implemented for local MVP administration | Identity model, admin user/organization APIs, Keycloak demo users, `/configurazione` UI, persisted menu/button translations | Admin can manage users, active state, roles, groups, partition, company, OIDC subject, fiscal code, contact data, counter-signer fiscal-code field, and UI texts; signer receives 403 on admin APIs. | Full IAM federation and SAC production integration. |
| Signature providers and accounts | Goal 2 / Goal 3 / Goal 4 | Implemented for technical administration | Provider/account schema, admin API/UI, signer identity model | Provider configuration, authentication mode, external credential reference and signer account/certificate aliases exist; provider passwords are not persisted. | Universal provider library and live authentication. |
| Signer report search | Goal 4 | Planned | Report model, authorization, frontend routes | Signer sees only assigned reports and can filter by simple/advanced criteria with pagination. | Cross-signer administrative visibility in signer UI. |
| Report preview | Goal 4 | Planned | Document storage, authorization, audit | Preview is controlled by temporary URL or stream and emits audit event. | Permanent public object URLs. |
| Single signature | Goal 4 | Planned | Mock provider adapter, report states, audit | One report can move through `SIGNING` to `SIGNED` or `SIGN_ERROR`; outcome is persisted and audited. | Real provider integration. |
| Batch signature | Goal 4 | Planned | SignatureBatch, SignatureAttempt, mock provider | Batch has global state plus per-document results; partial success is represented. | Treating a batch as atomic. |
| Source-system CRUD and flags | Goal 3 / Goal 4 / Goal 5 | Implemented for technical administration | SourceSystem schema, admin API/UI | Admin manages code, company code, CDA type, active state, description, `pdfA3Conversion`, `visibleSignature`, `multipleSignature`, `sendUnsigned`, `createCda`, and `passthrough`; incoherent combinations warn or fail validation. | Hard-coded behavior for specific upstream systems. |
| FSE facility mappings | Goal 3 / Goal 4 / Goal 8 | Implemented for technical administration | Mapping schema, admin API/UI | Admin manages facility, company, operating unit, source system, department, and active state for FSE metadata normalization. | Real FSE gateway submission. |
| HL7 intake and metadata monitoring | Goal 5 / Goal 6 | Planned | HAPI HL7v2, object storage, monitoring APIs | Input/output processed and discarded messages are searchable; detail shows masked payload, metadata, errors, and timeline. | Production MLLP routing and OpenSearch search. |
| Configurable ingestion pipeline | Goal 5 | Planned | SourceSystem, Hl7Message, Report, CdaBuilder, PdfA3Converter, event publisher | Mock intake creates/updates reports, applies configuration flags, handles missing signer/incomplete data, and publishes events. | Complete CDA2/FSE accreditation implementation. |
| Reports without signer queue | Goal 6 | Planned | Ingestion pipeline, report states, admin monitoring | Missing signer produces `MISSING_SIGNER` and appears in a dedicated operational queue. | Automated HR/master-data remediation. |
| Audit events | Goal 7 | Planned | Event schema, authorization hooks | Searches, previews, report access, signatures, provider auth, errors, FSE/conservation events, config changes, imports, and exports are append-only audited. | External SIEM integration. |
| Analytics events and SLA basis | Goal 7 | Planned | Event publisher, analytics writer | Events cover receipt, parsing, ready-to-sign, preview, signature request, signed, failure, FSE sent/accepted, conservation sent, and error with minimal sensitive data. | ClickHouse/Superset/Knowage before event stabilization. |
| FSE 2.0 validation and submission | Goal 8 | Planned | Official FSE assets, mapping model, signed document availability | Interfaces and states support validation, sent, accepted, rejected, retry, and reconciliation. | Real submission in MVP. |
| Digital preservation | Goal 9 | Planned | Document package metadata, preservation adapter | Packages and receipts can be referenced and correlated to reports. | Being an accredited conservator; real provider integration in MVP. |
| Advanced privacy/access layer | Goal 10 | Out of MVP | Authentication/authorization foundation | Future consent, blackout, access reason, emergency access, and consultation logging are designed after MVP. | Included in first MVP. |
| Full clinical repository | Goal 10 | Out of MVP | Document model, object storage, authorization | Future repository supports versions, metadata, episode/patient correlation, provenance, and controlled consultation. | Replacing enterprise DSE/LIS/RIS. |
| Advanced search and BI | Goal 10 | Out of MVP | Stable event model, OpenSearch, ClickHouse, BI tool | Future dashboards measure volumes, SLA, delays, errors, throughput, and trends. | OpenSearch/ClickHouse before event shape is stable. |

## MVP Summary

The MVP includes Goals 0 through 7 and only preparatory slices of Goals 8 and 9. Goal 10 is explicitly deferred.
