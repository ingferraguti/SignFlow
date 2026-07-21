# Development

Use `make up` for the full local stack and `make down` to stop it. Use `make backend-test`, `make frontend-test`, and `make lint` before opening changes.

Backend code uses Java 21, Spring Boot, Maven, Flyway, JPA, Bean Validation, Actuator, OpenAPI, JUnit 5, and Testcontainers. Keep module boundaries under `it.signflow` explicit and avoid circular dependencies.

Frontend code uses Next.js App Router, TypeScript, functional React components, ESLint, and global CSS for the current foundation. Do not add heavy UI libraries unless a future task explicitly requires one.

Never commit secrets or realistic personal, fiscal, or healthcare data.

## TLS inspection during container builds

The Dockerfiles accept an optional BuildKit secret named `local_ca`. This is useful when antivirus or a corporate proxy replaces public TLS certificates. Keep the public root certificate and a Compose override in an ignored local directory; never add the certificate to an image or commit it. Example override:

```yaml
services:
  backend:
    build:
      secrets: [local_ca]
  frontend:
    build:
      secrets: [local_ca]
secrets:
  local_ca:
    file: .local/local-root-ca.crt
```

Build with `docker compose -f compose.yaml -f .local/compose.tls.yaml build`. Do not disable Maven, npm, or Docker TLS verification.
