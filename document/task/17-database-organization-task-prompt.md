# Task Prompt: Enterprise Database Organization

## Objective
Organize database ownership so application data, governed business rules, and retrieval indexes are cleanly separated and Flyway-managed.

## Required Outcome
- Datasource data queried by the NL-to-SQL flow is managed by Flyway under `db/datasource/migration`.
- Governed business-rule storage is managed by Flyway under `db/governance/migration`.
- Retrieval/vector storage is managed by Flyway under `db/retrieval/migration`.
- PostgreSQL mode uses separate logical databases:
  - `nlda_app`
  - `nlda_governance`
  - `nlda_retrieval`
- Local H2 mode remains fast and self-contained, but still uses the same Flyway migration folders.
- Spring SQL init resource files are not used for schema/data ownership.

## Acceptance Criteria
- No active runtime config references root `schema.sql`, `data.sql`, `schema-postgres.sql`, or `data-postgres.sql`.
- `.\mvnw25.cmd test` passes.
- PostgreSQL profile starts with `governed-database,postgres-provider`.
- App queries execute against `nlda_app`.
- Business-rule admin APIs read/write `nlda_governance`.
- pgvector and pg_trgm indexes live in `nlda_retrieval`.
- Documentation explains the separation and Flyway folders.
