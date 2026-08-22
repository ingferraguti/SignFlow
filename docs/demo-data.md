# Fictional Demo Dataset

Every identity, patient, Report, HL7 message, identifier and document in the repository is synthetic. The values are
deliberately recognizable as tests and must never be replaced with real personal or health data in source control.

Local identities:

| Role | Username | Local password | Purpose |
| --- | --- | --- | --- |
| Administrator | `demo.admin` | `local-admin-password` | Configuration, monitoring and delivery workflows |
| Signer | `demo.signer` | `local-signer-password` | Primary profile of a fictional natural person |
| Signer alternate | `demo.signer.alt` | `local-signer-alt-password` | Second authentication profile of the same fictional person |
| Approver | `demo.approver` | `local-approver-password` | Independent review and approval |

All email addresses use `signflow.local` or `signflow.invalid`. Fiscal-code-shaped identifiers are an explicit
allowlist of synthetic fixtures checked by `scripts/scan-repository.ps1`; `TSTMVP90A01H501Q` is reserved for the
repeatable release E2E. Documents are minimal fictional PDFs or non-legal mock attestations. Raw HL7 examples use demo
source systems and synthetic patient/episode identifiers.

These passwords are local Compose fixtures, not secrets suitable for any shared or production environment. Replace
all credentials and demo realm material before any non-local deployment.
