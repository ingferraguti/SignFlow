# Domain Glossary

This glossary is the naming baseline for SignFlow. Use Italian business terms in user-facing text where helpful, but keep code-facing concepts stable and explicit.

## Core Terms

| Term | Italian term | Definition | Relationship |
| --- | --- | --- | --- |
| Practice | Pratica | Operational case/container used to group related work without replacing the central `Report` aggregate. | A Practice groups one or more Reports; every Report belongs to one Practice in the local domain model. |
| Report | Referto | Central workflow object to be signed, tracked, sent to FSE, and preserved. It combines metadata, state, signer assignment, source-system origin, and document references. | A Report references one or more Clinical Documents and is assigned to a Signer. |
| ClinicalDocument | Documento clinico | Technical artifact associated with a Report: PDF, PDF/A, CDA2, XML, signed document, FSE receipt, or preservation receipt. | Stored outside PostgreSQL in object storage; PostgreSQL stores metadata, hash, version, and references. |
| Signer | Firmatario | Clinical professional enabled to sign reports. | Can map to application users, domain accounts, and one or more signature-provider accounts. |
| Approver | Approvatore | Person who validates, counter-signs, delegates, or participates in a multi-signature approval workflow. | Not a first-class MVP role unless required by counter-signature rules; source material starts with a counter-signer fiscal-code field. |
| SourceSystem | Sistema erogante | Upstream clinical or legacy system that produces reports, HL7 messages, CDA, PDFs, or metadata. | Owns pipeline behavior through configuration flags. |
| SignatureProvider | Provider di firma | External remote-signature provider such as Aruba, Namirial, InfoCert, Intesi, or another provider. | Accessed through adapters. Provider-specific details stay outside the core domain and UI. |
| SignatureBatch | Batch di firma | Batch operation created for mass signature. It has a global state and a per-document outcome. | Contains many SignatureAttempts; it is not atomic. |

## Identity and Organization

| Term | Definition |
| --- | --- |
| Partition | Logical tenant or organizational boundary, for example `SIADOM` or `POLICLINICO`. |
| Company | Healthcare company or organization, for example AUSL, AOU, or NOS. |
| User | Application identity with username, domain, fiscal code, email, active state, partition, roles, groups, and optional signer mapping. |
| NaturalPerson | Canonical physical person with an immutable internal UUID. It can own multiple application profiles, authentication identities, and digital signatures. |
| PersonIdentifier | Qualified external identifier with scheme, issuing country, issuer, normalized value, and verification state. Italian tax code and eIDAS PersonIdentifier are identifiers, not database primary keys. |
| AuthenticationIdentity | Issuer-scoped login identity identified by `(issuer, subject)`, with method such as OIDC, LDAP, SPID, CIE, or eIDAS. |
| Role | Application authorization role. Initial roles: `Administrator` and `Firmatario`. |
| Group | Functional grouping used to associate users with providers, partitions, rules, or source systems. |
| SignatureAccount | Digital-signature/provider account owned by a NaturalPerson. A person can own more than one; each application profile can select a preferred one among those available. It must not store persistent provider passwords. |

## Source and Document Flow

| Term | Definition |
| --- | --- |
| FseFacilityMapping | Mapping between facility, company, operating unit, department, and source system for FSE metadata normalization. |
| FseDocumentType | Controlled high-level FSE 2.0 document nature (`REF`, `LDO`, `VRB`, and the other admitted class codes). Every Report and ClinicalDocument must reference one; it is distinct from a CDA implementation-guide/profile identifier. |
| SourceSystemFseDocumentType | Per-source-system allow-list that selects the FSE document types for which CDA preparation and PDF injection are enabled. Both the source-system pipeline and the type mapping must enable the behavior. |
| PatientMetadata | Minimal patient data needed to correlate and display a report. Must be minimized and protected. |
| Hl7Message | Input or output HL7 message metadata and processing state. Raw payload belongs in object storage and must be masked in UI/logs. |
| CdaBuilder | Interface responsible for building CDA2 when `SourceSystem.createCda` requires it. |
| PdfCdaInjector | Interface responsible for embedding an already generated CDA R2 as the case-insensitive `cda.xml` PDF associated file expected by the national FSE Gateway. It does not generate or clinically validate CDA content. |
| NationalFseGatewayConnector | Outbound port for future Gateway validation and publication. Application authentication, Gateway JWT construction, accreditation assets, and provider transport remain outside the core domain. |
| PdfA3Converter | Interface responsible for PDF/A3 conversion when configured by the source system. |

## Signature Flow

| Term | Definition |
| --- | --- |
| SignatureAttempt | One attempt to sign one report, either standalone or inside a batch. Stores state, retry count, provider error, timestamps, provider ids, and optional batch reference. |
| ProviderSession | Temporary authentication/session context with a signature provider. Must not persist provider credentials. |
| DigitalSignatureEngine | Provider-independent cryptographic component for document verification, PAdES construction, result validation, and signature-information extraction. It does not authenticate application users or remote providers. |
| ProviderCorrelationId | Non-sensitive identifier propagated through session, challenge, authentication, submit, polling, and retrieval operations for technical traceability. |
| Visible signature | Provider or document configuration that makes signature appearance visible in a PDF. |
| Multiple signature | Workflow where more than one signer/approver may be involved. The first MVP may model the flag and defer complex rules. |

## Audit and Analytics

| Term | Definition |
| --- | --- |
| AuditEvent | Append-only event for legal and operational traceability. Includes user action, state transition, or integration result. |
| AnalyticsEvent | Denormalized event shaped for later BI and SLA analysis. It should not include unnecessary sensitive data. |
| SLA | Measured time target for report production-to-signature, pickup, signature, FSE submission, preservation, and related dimensions. |

## Naming Guidance

- Prefer `Report` in code for the aggregate described as `Referto` in the source material.
- Prefer `ClinicalDocument` for stored artifacts; do not overload `Report` with binary content.
- Prefer `SourceSystem` over product-specific upstream names.
- Prefer `SignatureProvider` and adapter names for external providers.
- Keep `Approver` distinct from `Signer` when describing counter-signature or approval flows.
