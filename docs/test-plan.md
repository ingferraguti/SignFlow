# Test Plan

Last updated: 2026-08-17.

## Natural Person, Authentication Profiles, and Digital Signatures

Backend tests (`AdminApplicationUserIntegrationTest`, `SignerReportIntegrationTest`,
`MockSignatureWorkflowIntegrationTest`, `ReportReviewIntegrationTest`):

- Italian tax code, eIDAS `PersonIdentifier`, and national identifiers are normalized as qualified identifiers;
- two profiles with the same verified personal identifier resolve to one `NaturalPerson` even when authentication
  issuer/method differ or different issuers reuse the same subject;
- changing the natural-person association requires an administrative reason and produces an append-only identity event;
- direct Report assignment to any profile exposes the Report to every active signer profile of that person and to no
  other person; group and partition grants do not confer signing visibility;
- role separation compares natural persons, preventing the same person from approving through another profile;
- provider sessions, signature batches, idempotency keys, results, and artifacts are authorized by natural person;
- each application profile can select a preferred digital signature only from the active signatures owned by its
  natural person; another person's signature is rejected;
- multiple signers per Report remain explicitly deferred: `assigned_signer_id` is retained as the single assignment.

Frontend and E2E checks:

- administrator forms manage identifier scheme/country/issuer/value, authentication issuer/method, and reasoned
  identity correction without displaying or storing passwords;
- technical configuration associates one or more digital-signature entities with a natural person;
- signer profile shows a masked personal identifier, linked authentication accounts, available signatures, and a
  changeable preferred signature;
- desktop and 390 x 844 views have no page overflow, profile controls remain usable, relevant network calls return
  HTTP 200, and the JavaScript console is empty.

## Baseline Regression

Run these checks before committing feature goals:

```powershell
.\scripts\test-backend.ps1
.\scripts\test-frontend.ps1
```

When the Docker Compose stack is running, also run:

```powershell
.\scripts\test-e2e.ps1
```

## Goal 3 - Users, Roles, and Organization

Backend tests:

- `AuthorizationIntegrationTest` verifies protected API access, 401, 403, and current user claims.
- `AdminApplicationUserIntegrationTest` verifies:
  - administrator can search demo users;
  - administrator can read organization options;
  - signer receives 403 on administrative user APIs;
  - signer receives 403 on organization-management and UI-text APIs;
  - administrator can create, update, activate, and deactivate an application user;
  - administrator can create and update partitions, companies, and groups and change their active state;
  - administrator can configure persisted menu and button translations;
  - organization payloads are validated by the backend;
  - application user CRUD does not introduce password storage.

Frontend checks:

- `npm run lint`
- `npm run build`
- Playwright verification of `/configurazione` with the local Keycloak administrator, including users, organizational structure, and admin text/translation profile.

Non-regression checks:

- System status remains protected and available after login.
- Existing OIDC login/logout E2E still passes.

## Goal 4 - Technical Administration

Backend tests (`AdminTechnicalConfigurationIntegrationTest`):

- demo source system, signature provider, signature account, and FSE facility mapping are available;
- no provider/account database column or API response stores a password;
- an administrator can create, read, update, and delete all four configuration types;
- `createCda=true` with `passthrough=true` is rejected with an explicit 400 message;
- invalid provider authentication modes and non-HTTP provider URLs are rejected;
- signature accounts only accept users with the `SIGNER` role;
- FSE mappings require source system and company consistency;
- a signer receives 403 for every technical administration resource.

Frontend and E2E checks:

- lint and production build pass;
- `/configurazione` renders all four technical configuration tabs and demo records;
- the source-system form displays an explicit incompatibility error before submission;
- configurable admin texts include every new tab and action button.

Non-regression checks:

- all Goal 3 backend tests continue to pass;
- protected system status, OIDC login/logout, user administration, organization administration, and UI-text profile remain available.

## Goal 5 - Practices and Reports

Backend tests (`AdminReportIntegrationTest`):

- exact lookup by internal, external, and FSE identifier;
- deterministic identifier precedence: internal, then external, then FSE, with descriptive filters ignored;
- patient search by name and patient identifier;
- signer search and signer fiscal-code search;
- state, source-system, and department filters;
- production, modification, and signature date intervals;
- pagination and validated ordering;
- rejection of invalid pages, page sizes, ordering, states, date intervals, and UUID parameters;
- complete detail containing Practice, PatientMetadata, signer, SourceSystem, dates, flags, and state;
- every required Report state is represented;
- signer receives 403 on report administration search and detail.

Frontend and E2E checks:

- `/referti` is enabled in navigation and renders the advanced admin search;
- exact-ID precedence is communicated and descriptive fields are disabled during exact lookup;
- report table pagination and detail consultation work with seeded fictitious records;
- report action buttons remain configurable from the admin UI-text profile.

Non-regression checks:

- all Goal 3 and Goal 4 backend tests continue to pass;
- technical configuration, identity administration, protected system status, and OIDC login/logout remain operational.

## Goal 6 - Clinical Documents and Object Storage

Backend tests (`AdminClinicalDocumentIntegrationTest`):

- upload a real PDF payload to a MinIO Testcontainer and persist metadata only in PostgreSQL;
- verify SHA-256, effective MIME type, byte size, original filename, generated opaque object identifier, uploader, upload date, version, and active status;
- verify consecutive versions for the same Report;
- download the exact bytes with authorized inline/attachment responses and `nosniff` protection;
- generate and consume an expiring presigned URL;
- prove that the same private object is rejected without a signature;
- reject fake PDF content, unsafe filenames, wrong declared MIME types, oversize files, invalid dispositions, and missing Reports;
- logically delete metadata, block subsequent content access, and retain the underlying object;
- return 401 to anonymous callers and 403 to the unauthorized signer role;
- assert that `clinical_documents` contains no PostgreSQL `bytea` column.

Frontend and E2E checks:

- `/referti` detail lists the fictitious demo PDF and its metadata;
- an administrator uploads a PDF, sees the calculated metadata, opens a presigned preview, and downloads the original filename;
- document action labels are persisted in the admin text/translation profile;
- lint, type validation, and production build pass with binary/multipart proxying enabled.

Non-regression checks:

- all Goal 3, Goal 4, and Goal 5 backend integration tests pass;
- authentication, organization, technical configuration, report search/detail, protected system status, and OIDC login/logout remain operational.

## Goal 7 - Signer Portal

Backend tests (`SignerReportIntegrationTest`):

- direct assignment, group authorization, and partition authorization expose only the expected Reports;
- a foreign Report is hidden with 404 from search-adjacent detail and document APIs;
- simple and advanced filters cover patient, type, department, state, production/signature dates, paging, empty results, and invalid criteria;
- home counters, profile, state legend, and authenticated UI-text reading are available to the signer;
- administrator and anonymous access to signer APIs return 403 and 401;
- opening a real PDF through MinIO transitions a ready Report to `PREVIEWED`, while download returns the PDF bytes.

Frontend, network, and E2E checks (`signer.spec.ts`):

- Keycloak login as `demo.signer`, role-specific navigation, portal home, and logout;
- five directly/group/partition-authorized demo Reports are listed and `RPT-INT-005` remains invisible;
- simple search, detail, real PDF viewer, `PREVIEWED`, unavailable-document and incomplete-report states;
- legend, information, and profile pages, including the signer group;
- successful real `/api/backend/signer/reports` network response and no unexpected JavaScript console errors;
- responsive viewport at 390 x 844 has no page overflow, a horizontal menu, and table-contained scrolling.

Non-regression checks:

- the complete backend suite, frontend lint/build, and both admin/signer Playwright flows pass;
- the admin remains able to manage translated menu/button labels, while authenticated signers can read them without admin privileges.

## Goal 8 - Report Workflow and Assignment

Backend tests (`ReportWorkflowRulesTest`, `ReportWorkflowIntegrationTest`, `SignerReportIntegrationTest`):

- the complete explicit transition matrix covers every `ReportState` and rejects skipped or terminal transitions;
- signer assignment validates an active application user with the `SIGNER` role and detects missing signer data;
- missing document or mandatory metadata produces `INCOMPLETE`, missing signer data produces `MISSING_SIGNER`, and satisfied preconditions produce `READY_TO_SIGN`;
- the first controlled PDF preview records its timestamp and moves `READY_TO_SIGN` to `PREVIEWED` only through `ReportWorkflowService`;
- the same operation key is replayed without a second version increment or duplicate event, while reuse with different payload is rejected;
- two operations using the same expected version yield exactly one success and one optimistic-concurrency conflict;
- administrative correction requires a nonblank reason, is restricted to explicit recoverable states, and cannot override failed preconditions;
- the PostgreSQL guard rejects direct state or signer updates outside the workflow persistence path;
- administrator, signer, anonymous, malformed payload, invalid signer, stale version, and forbidden-transition responses are verified.

Frontend and E2E checks:

- `/referti` detail shows current state/version, first preview, missing preconditions, and append-only workflow history;
- the admin can assign or remove a signer, evaluate readiness, and submit a reasoned correction from graphical forms;
- action availability follows the current state and every mutation sends an operation key plus expected version;
- all workflow action labels are persisted and editable in `Profilo admin · Testi e traduzioni`;
- the demo flow uses only explicitly fictitious Reports, patients, documents, users, and identifiers;
- desktop and 390 x 844 layouts remain contained, workflow cards collapse to one column, and tables scroll inside their containers;
- browser checks cover successful workflow API calls, absence of unexpected JavaScript errors, and visible error feedback.

Non-regression checks:

- run `test-backend.ps1`, `test-frontend.ps1`, and `test-e2e.ps1` after the workflow-specific checks;
- verify admin report/document management and signer preview still pass through the dedicated workflow service;
- verify authentication, role navigation, organization and technical configuration, status page, and existing report searches.

## Goal 9 - Review, Approval, and Counter-signature Preparation

Backend tests (`ReportReviewIntegrationTest`, `ReportWorkflowRulesTest`):

- the assigned signer requests review only after preview and the state moves from `PREVIEWED` to `REVIEW_PENDING`;
- the assigned approver records document view, approves to `APPROVED`, or rejects to `PREVIEWED` with a mandatory reason;
- an administrator can return `APPROVED` to `REVIEW_PENDING` and `REVIEW_PENDING` to `PREVIEWED`, always with a reason;
- role separation blocks an approver who is also the signer, producer, or active-document uploader when independence is required;
- `APPROVER`, `SIGNER`, and `ADMINISTRATOR` endpoints reject callers with the wrong role;
- repeated operation keys replay the stored result, while concurrent approvals using the same expected version yield one success and one conflict;
- counter-signature preparation records participant and timestamp while leaving `signed_at` empty and state `APPROVED`;
- the append-only decision timeline records request, view, approval, rejection, return, configuration, and counter-signature preparation.

Frontend, network, and browser checks (`zz-review.spec.ts` plus integrated browser):

- signer and approver detail show preview, review, and signature as three explicit steps;
- `/approvazioni` exposes queue, PDF view, approval, reason-required rejection, and decision timeline only to the approver;
- admin Report detail configures approver, separation, controlled return, and counter-signature preparation;
- new menu and action labels are persisted in `Profilo admin - Testi e traduzioni`;
- review request, document view, and approval POSTs return HTTP 200;
- desktop and 390 x 844 layouts have no page overflow and the stepper changes from three columns to one;
- the integrated browser confirms the PDF iframe, `VIEWED`/`APPROVED` timeline entries, success feedback, and an empty JavaScript console.

Non-regression checks:

- run `test-backend.ps1`, `test-frontend.ps1`, and `test-e2e.ps1`;
- verify all 81 backend tests pass;
- verify existing administrator and signer E2E flows remain green before the review E2E.

## Goal 10 - Single and Batch Mock Signature

Backend tests (`MockSignatureWorkflowIntegrationTest`, `ReportWorkflowRulesTest`):

- a temporary provider session accepts only the explicit demo code, expires after five minutes, and persists no submitted credential;
- a successful single operation reaches `SIGNED`, stores `signature_kind=MOCK`, and exposes only a text artifact marked `MOCK ONLY`;
- a planned single failure reaches `SIGN_ERROR` with provider error code/message and no artifact;
- a filtered “firma tutti” batch snapshots all matching visible `APPROVED` Reports and completes with per-document results;
- a manual batch preserves successful attempts and reports `PARTIAL_SUCCESS` when another document fails;
- a bounded retry increments `retryCount` and can turn the configured fail-once scenario into success;
- a repeated single submission with the same operation key returns the original batch without duplicate attempts;
- a confirmed batch can be cancelled before start and returns reserved Reports from `SIGN_BATCH_CREATED` to `APPROVED`;
- every Report state transition is recorded by `ReportWorkflowService`; direct SQL state changes remain guarded;
- the full backend suite contains 71 passing tests.

Frontend, network, and browser checks (`zzz-signature.spec.ts` plus integrated browser):

- the Report detail preserves the explicit Anteprima, Revisione, Firma sequence and labels the provider as non-legal mock;
- selection checkboxes are enabled only for `APPROVED` Reports, with manual batch and filtered “firma tutti” controls;
- `/firma/batch` lists batches and the detail exposes provider session, confirmation, start, pre-start cancellation, retry, per-document result, and final summary;
- every new menu/button label is persisted and editable in the administrator text/translation profile;
- provider-session and single-signature POSTs return HTTP 200 and lead to `COMPLETED`/`SUCCEEDED`;
- downloadable output is a text attestation with the `MOCK-NON-LEGAL` response header, never a signed PDF;
- 1440 x 900 and 390 x 844 layouts have no page overflow, the batch summary changes from four to two columns, and the JavaScript console is empty;
- the E2E runner restores only the four fully fictitious signature fixtures before and after the suite.

Non-regression checks:

- run `test-backend.ps1`, `test-frontend.ps1`, and `test-e2e.ps1`;
- verify all 81 backend tests, frontend lint/build, and all 4 Playwright flows;
- verify authentication, admin CRUD, documents, explicit workflow, independent review, signer preview, configurable labels, and responsive navigation.

## Goal 11 - Digital Signature Engine and Provider Adapters

PAdES engine tests (`DssPadesSignatureEngineTest`):

- generate a new RSA private key, self-signed certificate, and PKCS#12 entirely in memory with a fictional test subject;
- verify an unsigned fictional PDF is recognized as PDF and contains no signature;
- create a SHA-256 PAdES Baseline B signature through EU DSS 6.4 and the PDFBox implementation;
- validate the output against the explicit local test trust anchor and require `TOTAL_PASSED`;
- extract signature format, indication, signer/subject, issuer, serial number, digest algorithm, signing time, and
  certificate validity interval;
- reject non-PDF input and an invalid PKCS#12 password;
- ensure no generated test key or authorization secret is persisted by the application.

Shared adapter contract tests (`SignatureProviderAdapterContractTest`):

- run the same lifecycle tests against `MockSignatureProvider` and `LocalTestSignatureProviderAdapter`;
- open a temporary session, obtain a challenge, authenticate, submit, poll, and retrieve the result;
- require correlation ID propagation through every response;
- repeat a submission with the same idempotency key and require the same provider operation reference;
- require typed, non-retryable authentication failures and typed, retryable timeout failures;
- exercise both document and digest submission modes through the common response/error contract;
- keep the local PAdES adapter outside the production Spring component registry.

Workflow and migration regression:

- run `MockSignatureWorkflowIntegrationTest` after the adapter refactor and require all seven single/batch mock
  scenarios to remain green;
- validate Flyway V16 on a fresh PostgreSQL 16 container and confirm the session table stores only opaque references;
- run `test-backend.ps1`, `test-frontend.ps1`, `test-e2e.ps1`, and finally `test-all.ps1`;
- require all 81 backend tests, frontend lint/build, and the four existing Playwright flows to pass;
- scan domain and UI changes to ensure no real provider brand, endpoint, or provider DTO was introduced.

## Goal 7 / Delivery Objective 12 - Append-only Audit and Monitoring

Focused backend tests (`AuditIntegrationTest`):

- Flyway V17 creates the ledger, retention policy, privacy-safe fictional FSE/preservation events, and source-table triggers on a fresh PostgreSQL 16 database;
- Flyway V21 creates partial expression indexes for document/report and signature-batch/report audit links;
- direct `UPDATE` and arbitrary `DELETE` of an audit event fail at database level;
- forbidden sensitive metadata keys and nested/non-scalar values are rejected before persistence;
- login and sensitive Report searches preserve a valid correlation ID and actor;
- document upload produces an event through the database trigger, and document-open events linked by document ID
  appear in the related Report timeline without duplicating clinical metadata;
- event type filters, pagination, and minimal metadata serialization are verified;
- a complete Italian tax code is rejected even when disguised under a generic metadata key;
- a signer receives 403 from CSV export while an administrator receives a CSV attachment;
- retention accepts only 30-3650 days, records configuration change, and removal is restricted to expired entries through the dedicated transaction.

Frontend and integrated-browser checks:

- `/monitoraggio` is enabled only in administrator navigation and exposes event/actor/correlation/entity/outcome/date filters, pagination, CSV export, and retention controls;
- the Report detail exposes a chronological application timeline combining Report, review, document, and signature events;
- batch creation/state and provider-attempt events are resolved through append-only audit links and appear in
  the related Report timeline even when source rows are unavailable and metadata omits clinical identifiers;
- audit menu, buttons, and section titles are editable in `Profilo admin - Testi e traduzioni`;
- completely fictional FSE and preservation events are visible without clinical payloads;
- filter and CSV requests complete successfully and the JavaScript console is empty;
- 1440 x 900 and 390 x 844 layouts have no page-level horizontal overflow; the table and mobile navigation scroll only inside their containers.

Non-regression checks:

- run `test-backend.ps1`, `test-frontend.ps1`, `test-e2e.ps1`, and `test-all.ps1`;
- verify workflow, review, signature, document, identity, organization, technical configuration, authorization, and PAdES/adapter suites remain green;
- verify every workflow/review/signature test fixture can be reset without mutating existing audit rows;
- verify the Docker Compose stack migrates from V16 to V17 and all five services reach healthy/running state.

## Goal 5 / Goal 6 / Delivery Objective 13 - API/HL7 Ingestion and Document Pipeline

Focused backend tests (`Hl7V2ParserTest`, `Hl7IngestionIntegrationTest`, `LocalMllpListenerTest`):

- parse and initially validate completely fictional HL7 v2.5 ORU^R01 and MDM^T02 messages through HAPI HL7;
- extract minimized patient, episode, Report, signer, SourceSystem, and embedded ED/Base64 PDF data;
- reject malformed and unsupported messages without returning or logging their sensitive content;
- accept protected REST ingestion only for `INGESTION` or `ADMINISTRATOR` and return the correlation ID;
- accept a real local MLLP frame and return `MSA|AA`/`MSA|AE` without reflecting patient content;
- save the raw HL7 object in private MinIO and only payload hash, object key, retention, and processing metadata in PostgreSQL;
- verify `createCda`, normalization, mock PDF/A-3 conversion, and passthrough behavior from SourceSystem configuration;
- produce `READY_TO_SIGN`, `MISSING_SIGNER`, or `INCOMPLETE` only through `ReportWorkflowService`;
- return the original result for an identical retry, discard a changed payload reusing the control ID, and create exactly
  one Report under two concurrent identical submissions;
- expose filtered/paginated processed and discarded messages, a masked raw preview, safe error codes, and the missing-signer queue;
- reconstruct receipt, workflow transitions, document association, and processing outcome in the append-only Report timeline.

Frontend, network, and integrated-browser checks:

- `/monitoraggio` displays the three fictional demo outcomes, filters, pagination, pipeline detail, and missing-signer queue;
- opening a detail performs an HTTP 200 request and shows segment names with masked content, never fiscal/patient/Base64 data;
- ingestion title and action-button texts are editable in `Profilo admin - Testi e traduzioni`;
- 1280 x 720 and 390 x 844 layouts have no page-level horizontal overflow; only the table/raw containers may scroll;
- the browser console contains no JavaScript errors and REST calls for message list/detail/missing-signer queue complete successfully.

Non-regression checks:

- run `test-backend.ps1`, `test-frontend.ps1`, `test-e2e.ps1`, and finally `test-all.ps1`;
- require all prior authentication, identity, configuration, documents, workflow, review, mock signature, PAdES adapter,
  audit, export, retention, and responsive-layout scenarios to remain green;
- rebuild the Docker Compose stack, require Flyway V22 and all services healthy, and verify MLLP is published on loopback only.
