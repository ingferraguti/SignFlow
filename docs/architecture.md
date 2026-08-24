# Architecture

SignFlow starts as a modular monolith. One backend application contains explicit package boundaries for shared concerns, identity, reports, signatures, source systems, audit, and configuration. This keeps deployment simple while preserving a path to stricter boundaries later.

The frontend and backend are separated. The frontend is a Next.js administrative UI and communicates with the backend through HTTP APIs. The backend owns transactional behavior and database access.

PostgreSQL is the primary transactional database and Flyway owns schema evolution. Clinical-document rows contain metadata and opaque object identifiers only; no binary document content is stored in PostgreSQL.

The inbound `ingestion` boundary supports both HL7 Report creation and a generic service-signature-request envelope.
The latter is not forced into `Report`: administrative documents have no patient/encounter semantics, while a
healthcare request can be mapped explicitly once the clinical metadata required by the Report workflow exists. Its
storage model keeps immutable `ORIGINAL`, `NORMALIZED_PDFA3`, and optional `SIGNED` artifacts. The normalized artifact
is produced by a raster-safe PDFBox pipeline and must pass the veraPDF PDF/A-3B profile before receipt succeeds.

MinIO provides the local S3-compatible object store. The document bucket is private: authorized APIs mediate uploads and downloads, while PDF previews may use short-lived presigned URLs. Internal object keys are generated independently from the original filename. Service-intake PDF rows, like ClinicalDocument rows, contain metadata and opaque object identifiers only.

Signature and preservation outcomes enter through role-separated adapter endpoints. A returned PAdES is accepted only
when EU DSS verifies its technical integrity and proves it covers the exact normalized artifact. Future provider APIs
remain behind adapters so provider-specific authentication and transport do not leak into core workflow code.

Future audit and analytics events should be produced from state transitions. This foundation does not include Kafka, ClickHouse, OpenSearch, or other event infrastructure.

Application authentication is separate from signature-provider authentication. User access to SignFlow and delegated authentication with remote signature providers must remain distinct concerns.
