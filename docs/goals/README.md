# Goals

This directory tracks implementation goals derived from `documento di progetto.md` and summarized in `docs/roadmap.md`.

## Goal Rules

- Each goal must link back to the product specification and acceptance matrix.
- A goal is complete only when its acceptance criteria are implemented and verified with current evidence.
- Documentation-only goals must not change application behavior.
- Implementation goals should update `docs/current-status.md` when the tested baseline changes.
- New features must not invent scope outside `documento di progetto.md` unless the product documentation is updated first.

## Current Goal Map

| Goal | Name | MVP | Status source |
| --- | --- | --- | --- |
| Goal 0 | Technical Foundation | Yes | `docs/current-status.md` |
| Goal 1 | Executable Specification and Traceability | Yes | `docs/product-specification.md`, `docs/acceptance-matrix.md` |
| Goal 2 | Domain Model and Persistence | Yes | `docs/roadmap.md` |
| Goal 3 | Admin Configuration | Yes | `docs/roadmap.md` |
| Goal 4 | Signer Workflow With Mock Provider | Yes | `docs/roadmap.md` |
| Goal 5 | Configurable Ingestion Pipeline | Yes | `docs/roadmap.md` |
| Goal 6 | Monitoring and Operational Queues | Yes | `docs/roadmap.md` |
| Goal 7 | Audit and Analytics Event Foundation | Yes | `docs/roadmap.md` |
| Goal 8 | FSE 2.0 Preparation | Partial | `docs/roadmap.md` |
| Goal 9 | Digital Preservation Preparation | Partial | `docs/roadmap.md` |
| Goal 10 | Advanced Repository, Privacy, and BI | No | `docs/roadmap.md` |

## Suggested Next Goal File Pattern

For future work, create one file per goal using:

```text
docs/goals/goal-N-short-name.md
```

Each goal file should include:

- Objective.
- In scope.
- Out of scope.
- Dependencies.
- Acceptance criteria.
- Verification commands.
- Current implementation notes.
