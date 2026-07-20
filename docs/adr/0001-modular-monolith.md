# ADR 0001: Modular monolith

## Status

Accepted

## Context

SignFlow needs a simple foundation that can evolve toward multiple healthcare workflow capabilities without premature distributed-system complexity.

## Decision

Start with one backend deployable organized by functional package boundaries and one frontend application in the same repository.

## Consequences

Development and local operation stay simple. Module boundaries must be maintained by convention until stricter enforcement is introduced.
