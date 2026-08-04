---
description: "Replace hardcoded retrieval chunks with dynamic schema indexing and pgvector-backed retrieval"
name: "Implement Dynamic Schema Retrieval"
argument-hint: "Optional focus, such as JDBC metadata indexing, pgvector storage, business rules, or refresh endpoint"
agent: "agent"
model: "GPT-5 (copilot)"
---
Implement dynamic schema and business-rule retrieval for the natural language database agent.

Use these documents:
- [Technology Stack](../../document/03-technology-stack.md)
- [Architecture Overview](../../document/04-architecture-overview.md)
- [Implementation Steps](../../document/06-implementation-steps.md)
- [Prompt Library](../../document/07-prompt-library.md)
- [Retrieval Failure Recovery](../../document/08-retrieval-failure-recovery.md)
- [Workspace Copilot Instructions](../copilot-instructions.md)

Task:
- Replace or extend the current in-memory hardcoded retrieval chunks with dynamically generated schema chunks.
- Use JDBC metadata or `information_schema` to extract tables, columns, primary keys, foreign keys, nullable fields, and data types.
- Keep curated business rules separate from physical schema metadata.
- Prepare retrieval storage for PostgreSQL + pgvector while preserving H2-compatible local tests.
- Keep `SchemaRetriever` as the stable retrieval interface.

Required behavior:
- generate schema chunks from the active datasource
- include join-path information from foreign keys when available
- include business rules and aliases from a curated source
- compute a schema fingerprint/hash and avoid unnecessary re-indexing when unchanged
- support refresh/rebuild behavior for schema drift
- preserve retrieval recovery flow: normalized, expanded, hybrid, fallback cache, safe clarification
- do not generate SQL when retrieval confidence remains below threshold

Implementation constraints:
- do not hardcode table chunks in retrieval logic except as a fallback/test fixture
- keep pgvector code isolated behind repository/service boundaries
- keep local H2 tests deterministic
- do not require a live PostgreSQL instance for the default test suite
- log indexing and retrieval attempts with traceable metadata

Tests to add:
- dynamic extraction of demo H2 schema
- chunk generation includes table, columns, keys, and joins
- business-rule chunks are included separately
- schema fingerprint changes when metadata changes
- retrieval works after refresh/rebuild
- fallback remains safe when dynamic indexing fails

Deliverables:
- schema metadata extractor
- schema chunk builder
- dynamic retrieval/indexing service
- pgvector-ready repository abstraction
- tests proving dynamic retrieval and safe fallback behavior
