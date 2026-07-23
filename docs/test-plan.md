# Test Plan

Last updated: 2026-07-23.

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
