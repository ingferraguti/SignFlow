# AGENTS.md

## Project Scope

SignFlow is a modular-monolith foundation for an open source healthcare middleware that orchestrates clinical document intake, remote digital signatures, audit, and future FSE/conservation integrations.

The current repository is intentionally a technical foundation. Do not treat placeholder frontend routes or empty backend package boundaries as implemented business features.

## Authoritative Documents

- `documento di progetto.md` is the reorganized source material extracted from `gestore firma.pdf`.
- `docs/product-specification.md` is the executable technical specification for planning and implementation.
- `docs/domain-glossary.md` defines domain language and must be consulted before naming new domain objects.
- `docs/roadmap.md` defines delivery goals and MVP boundaries.
- `docs/acceptance-matrix.md` maps known functional requirements to goals, current state, dependencies, acceptance criteria, and MVP exclusions.
- `docs/current-status.md` records the implementation and test/build baseline.
- `docs/goals/README.md` explains how goals are tracked.

## Working Rules

- Do not implement business functionality while updating specification, roadmap, or status documents.
- Preserve the existing modular boundaries under `backend/src/main/java/it/signflow`.
- Keep application authentication separate from remote signature-provider authentication.
- Keep provider-specific APIs behind adapters; do not leak Aruba, Namirial, InfoCert, Intesi, or similar details into core domain contracts or UI assumptions.
- Use `Report`/`Referto` for the central clinical signing workflow concept. If code or imported requirements use `Practice`, document the mapping to `Report`/`Referto` before renaming anything.
- Use `SourceSystem`/`Sistema erogante` as the configuration root for document pipeline behavior.
- Use append-only audit/event thinking for state transitions, even before the event store exists.
- Do not store provider passwords or sensitive clinical data in logs, analytics events, or test fixtures.
- Keep fixtures fictional and non-realistic enough to avoid accidental personal, fiscal, or healthcare data.

## Validation Baseline

Before changing implementation behavior, run the relevant existing checks:

- Backend: `.\scripts\test-backend.ps1`
- Frontend: `.\scripts\test-frontend.ps1`
- Full local baseline: `.\scripts\test-all.ps1`

Update `docs/current-status.md` whenever the baseline changes materially.
