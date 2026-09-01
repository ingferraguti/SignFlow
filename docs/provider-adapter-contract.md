# Provider-Neutral Digital Signature Contract

## Scope

`SignatureProviderAdapter` is the only application boundary for a remote signature provider. Core Report workflow,
database states, and web UI use neutral terms and must not depend on a vendor SDK, endpoint, error code, or brand.

The lifecycle is:

1. `openSession`: create a short-lived provider context for an account alias.
2. `requestChallenge`: request an OTP, push, or equivalent challenge without exposing the secret.
3. `authenticate`: submit the transient authorization response.
4. `submit`: send either a document or digest with an idempotency key and retry policy.
5. `poll`: obtain `ACCEPTED`, `PROCESSING`, `SUCCEEDED`, or `FAILED` for the provider operation.
6. `retrieve`: download the completed signed document.

Every command carries a timeout and correlation ID. Every response echoes the correlation ID. Errors use the common
categories `AUTHENTICATION`, `VALIDATION`, `TEMPORARY`, `TIMEOUT`, `PROVIDER`, and `NOT_FOUND`, together with a
provider-neutral retryable flag. Provider-specific error bodies must be translated in the adapter and must not reach
the domain or UI.

Provider sessions persist only opaque session/challenge references, expiry, state, and correlation ID. OTPs,
passwords, private keys, document content, and provider response bodies are not stored in the session table or logs.

## Local Test Implementation

`DssPadesSignatureEngine` uses EU DSS 6.4 with the PDFBox PAdES implementation. Automated tests generate a new
self-signed RSA certificate and PKCS#12 in memory with a fictional subject. The local adapter is not a Spring
component and cannot be selected by normal application configuration.

The engine creates PAdES Baseline B using SHA-256, validates the produced PDF with the test certificate as an explicit
local trust anchor, and extracts signature format, validation indication, signer/subject, issuer, serial number,
digest algorithm, signing time, and certificate validity interval. This proves technical interoperability only: it
does not produce a qualified signature, use a qualified certificate, contact a timestamp authority, or have legal
validity.

The UI workflow from Delivery Objective 10 continues to use the unmistakably non-legal plain-text mock provider.
No cryptographic test PDF is presented as a production or legally valid artifact.

## Aruba ARSS Adapter

`ARUBA_ARSS` is a real-provider adapter for Aruba Firma Remota. It uses the ARSS demo SOAP service documented in
`aruba/FirmaRemota.pdf`: `opensession` authenticates the supplied OTP and `pdfsignatureV2` synchronously returns the
signed PDF. The neutral `poll` and `retrieve` methods expose that synchronous result without leaking SOAP DTOs.

The adapter is configured as the initially inactive `ARUBA-REMOTE` provider, but no account is seeded and no credential is stored in the database.
Set `SIGNFLOW_ARUBA_USERNAME` and `SIGNFLOW_ARUBA_PASSWORD` through the deployment secret mechanism; the OTP is the
transient value posted to the existing provider-session endpoint. Do not add demo credentials to source, fixtures,
logs, or `.env` files. The provider must be tested with a non-production account before use in a clinical flow.

## Real Provider Integration Gate

Before enabling any real provider in production, confirm:

- current provider API and authentication/challenge documentation;
- sandbox base URL, client identity, non-production account, and credential-vault reference;
- supported signing input (document, digest, DTBS, or external CMS) and required digest/signature algorithms;
- test certificate chain and documented trust/revocation behavior;
- operation status, polling/callback protocol, timeout, retry, idempotency, and rate-limit rules;
- signed-document retrieval rules and integrity checks;
- complete provider error catalogue and mapping to the neutral error categories;
- security, privacy, audit, and legal/compliance approval for the intended signature level.

When these inputs exist, the adapter must pass `SignatureProviderAdapterContractTest` unchanged. Provider-specific
tests may be added, but weakening the shared contract is not allowed.
