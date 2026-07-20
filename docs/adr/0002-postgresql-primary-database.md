# ADR 0002: PostgreSQL as primary database

## Status

Accepted

## Context

The foundation needs a reliable relational database for transactional state and schema migrations.

## Decision

Use PostgreSQL as the primary transactional database and Flyway for database migrations.

## Consequences

The application has a mature relational store from the first increment. Document object storage, search, analytics, and event stores remain future concerns.
