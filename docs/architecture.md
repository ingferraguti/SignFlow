# Architecture

SignFlow starts as a modular monolith. One backend application contains explicit package boundaries for shared concerns, identity, reports, signatures, source systems, audit, and configuration. This keeps deployment simple while preserving a path to stricter boundaries later.

The frontend and backend are separated. The frontend is a Next.js administrative UI and communicates with the backend through HTTP APIs. The backend owns transactional behavior and database access.

PostgreSQL is the primary transactional database. Flyway owns schema evolution. The first migration only creates a technical metadata table to validate migrations.

Future object storage may be introduced for document binaries and previews. It is not implemented in this foundation.

Future signature providers should be integrated through adapter modules so provider-specific authentication and APIs do not leak into core workflow code.

Future audit and analytics events should be produced from state transitions. This foundation does not include Kafka, ClickHouse, OpenSearch, or other event infrastructure.

Application authentication is separate from signature-provider authentication. User access to SignFlow and delegated authentication with remote signature providers must remain distinct concerns.
