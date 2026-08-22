# SignFlow MVP 0.1.0 Runbook

This runbook reproduces the local MVP qualification. It never contacts a healthcare, signature-provider, FSE or
preservation production endpoint.

## Prerequisites

- Windows PowerShell 7, Docker Desktop with Compose, JDK 21, Maven, Node.js 22 and npm.
- Free loopback ports 3300, 18081, 18082, 55432, 19000, 19001 and 12575, or alternative parameters supplied to the verifier.
- Enough disk space for PostgreSQL/Testcontainers images, Maven/NPM caches and the pinned Trivy vulnerability database.

## One-command qualification

```powershell
.\scripts\verify-mvp-release.ps1
```

The script uses an isolated Compose project and temporary Keycloak realm redirect, runs repository and dependency
scans, migration tests, backend/frontend checks, a production image build, health checks, the full Playwright suite,
and a PostgreSQL/MinIO backup/restore round trip. It tears down the isolated volumes after completion. Evidence is
written to `.local/release-evidence/0.1.0-*.json` with `verified=true` only after every check passes.

Use `-KeepStack` only for investigation. The selected ports can be overridden with the named parameters documented
by `Get-Help .\scripts\verify-mvp-release.ps1 -Detailed` or by reading the script header.

## Manual demonstration

1. Copy `.env.example` to the ignored `.env` and run `.\scripts\start.ps1`.
2. Run `.\scripts\test-health.ps1`.
3. Open `http://localhost:3000` and use only the identities in `docs/demo-data.md`.
4. Run `.\scripts\test-e2e.ps1 -Grep '^MVP:'` for the complete flow, or omit `-Grep` for regression.
5. Inspect `/monitoraggio` for audit/HL7 history and `/integrazioni` for FSE/conservation mock outcomes.
6. Run `.\scripts\stop.ps1` when finished.

## Backup and restore

```powershell
.\scripts\backup-local.ps1 -Destination .local\backups\manual-check
.\scripts\restore-local.ps1 -BackupPath .local\backups\manual-check
```

The backup contains `postgres.dump`, the object-storage tree and `manifest.json` with database checksum and object
count. Restore replaces the local database and bucket; without `-Force` it requires typing `RESTORE`. Never point it
at a production environment.

## Failure handling

- Health failures: run `.\scripts\logs.ps1`, then `docker compose ps`; do not release while any service is unhealthy.
- Migration failure: preserve the failing Testcontainers/Flyway report and correct the migration; never edit an
  already-released migration.
- E2E failure: inspect `frontend/test-results`; cleanup is idempotent and removes only the fixed fictional MVP flow.
- Dependency finding: upgrade or replace the dependency, document a time-bounded exception only after security review,
  and rerun the full gate. The `0.1.0` gate has no automatic exception mechanism.
- Interrupted restore: keep the backup, restart PostgreSQL and MinIO, then rerun restore before starting applications.

## Release procedure

1. Confirm every MVP row in `docs/acceptance-matrix.md` is verified.
2. Run the one-command qualification from a clean worktree at the intended commit.
3. Confirm `git status`, review the release notes and commit.
4. Push the branch, create annotated tag `v0.1.0`, push the tag and publish the GitHub release from
   `docs/releases/0.1.0.md`.
5. Record limitations: signatures, FSE and conservation are mock/local test functions and have no legal or clinical validity.
