# Development

Use `make up` for the full local stack and `make down` to stop it. Use `make backend-test`, `make frontend-test`, and `make lint` before opening changes.

Backend code uses Java 21, Spring Boot, Maven, Flyway, JPA, Bean Validation, Actuator, OpenAPI, JUnit 5, and Testcontainers. Keep module boundaries under `it.signflow` explicit and avoid circular dependencies.

Frontend code uses Next.js App Router, TypeScript, functional React components, ESLint, and global CSS for the current foundation. Do not add heavy UI libraries unless a future task explicitly requires one.

Never commit secrets or realistic personal, fiscal, or healthcare data.
