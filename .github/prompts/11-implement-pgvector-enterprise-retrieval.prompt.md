---
description: "Implement pgvector-backed embeddings and the full enterprise retrieval pipeline"
name: "Implement pgvector Enterprise Retrieval"
argument-hint: "Optional focus, such as embedding client, pgvector schema, business-rule embeddings, schema embeddings, hybrid reranking, or retrieval pipeline"
agent: "agent"
model: "GPT-5 (copilot)"
---
Implement pgvector-backed semantic retrieval and the full enterprise retrieval pipeline for the natural language database agent.

Use these documents:
- [Retrieval Design](../../document/09-retrieval-design.md)
- [Pre-Evaluate RAG Note](../../document/note/01-pre-evaluate-rag.md)
- [Schema Context And Business Rule Cost Note](../../document/note/03-schema-context-and-business-rule-cost.md)
- [Dialect-Aware SQL Prompting Note](../../document/note/02-dialect-aware-sql-prompting.md)
- [Architecture Overview](../../document/04-architecture-overview.md)
- [Technology Stack](../../document/03-technology-stack.md)
- [Workspace Copilot Instructions](../copilot-instructions.md)

Prerequisite:
- This prompt assumes input normalization and typo correction are handled before retrieval, preferably by the pg_trgm prompt.

Goal:
- Store approved retrieval knowledge in governed tables.
- Generate embeddings for approved business rules, schema chunks, aliases, and join paths.
- Use PostgreSQL `pgvector` for semantic search.
- Merge semantic results with lexical/fuzzy results into typed, validated retrieval context.
- Send only approved, fresh, typed context to SQL generation.

Out of scope:
- enterprise memory/context
- previous result references
- UI/admin workflow unless minimal endpoints are needed for refresh/reindex
- making OpenAI or PostgreSQL required for the default unit test suite

Required design:
```text
processed user question
-> lexical/fuzzy retrieval candidates
-> embedding generation for query
-> pgvector semantic search
-> merge typed candidates
-> schema-ref validation
-> join-path validation
-> deterministic rerank
-> confidence pre-evaluation
-> typed prompt context
```

Tasks:
1. Add embedding provider abstraction.
   - Use `EmbeddingClient` as the stable interface.
   - Add LangChain4j/OpenAI implementation behind configuration.
   - Add fake deterministic embedding client for tests.
   - Configure provider, model, dimensions, timeout, and base URL through environment/properties.

2. Add pgvector storage.
   - Add migration for `CREATE EXTENSION IF NOT EXISTS vector`.
   - Add tables for chunk embeddings or equivalent normalized model.
   - Store chunk id, kind, source id, text, schema refs, aliases, datasource id, schema fingerprint, content hash, embedding model, embedding dimensions, active flag, and vector.
   - Add vector indexes appropriate for pgvector.

3. Build embedding indexing flow.
   - Generate canonical text for each typed chunk.
   - Embed only active/approved/fresh chunks.
   - Skip unchanged chunks by content hash.
   - Rebuild embeddings when schema fingerprint, business-rule version, or embedding model changes.
   - Log indexed chunk count, skipped count, failed count, model, and fingerprint.

4. Implement semantic retrieval.
   - Add `VectorRetrievalRepository` backed by pgvector.
   - Search with metadata filters: datasource id, schema fingerprint, chunk kind, active flag, approval status where available.
   - Return typed `RetrievedChunk` results with similarity score and source metadata.
   - Keep H2/default test fallback with fake vector retrieval.

5. Implement hybrid merge and reranking.
   - Merge lexical/fuzzy candidates from schema/vocabulary retrieval with vector candidates.
   - De-duplicate by chunk id.
   - Rerank using configurable signals:
     - vector similarity
     - exact token match
     - alias match
     - schema coverage
     - business-rule coverage
     - join-path coverage
     - policy compatibility
     - freshness
     - typo correction confidence
   - Do not use hardcoded domain boosts.

6. Validate before prompt construction.
   - Reject or downgrade chunks with stale schema refs.
   - Confirm join paths reference known tables/columns.
   - Confirm policy and datasource filters.
   - Return clarification when confidence or coverage is too low.

7. Preserve typed prompt context.
   - Prompt builder should receive typed context categories, not one untraceable text list.
   - Include schema, business rules, aliases, join paths, dialect, confidence, and missing fields.
   - Preserve chunk ids and versions for audit.

Required behavior:
- semantically related terms can retrieve approved business rules, for example `spending` -> revenue rule.
- schema and business-rule retrieval are merged safely.
- stale business rules or stale embeddings are not used as approved context.
- query embedding calls happen at retrieval time only.
- chunk embeddings are generated during indexing/refresh, not repeatedly during SQL generation.
- default tests pass without PostgreSQL, pgvector, OpenAI, or network.

Implementation constraints:
- do not use vector search as the source of truth
- source of truth remains governed DB/config/business-rule records
- keep pgvector SQL isolated behind repository boundaries
- keep provider configuration environment-driven
- never hardcode API keys
- preserve H2 demo behavior
- preserve REST response shape
- add audit logs for retrieved chunk ids, kinds, scores, schema fingerprint, and embedding model

Tests to add or update:
- fake embedding client is deterministic
- canonical chunk text produces stable content hash
- unchanged chunks skip re-embedding
- changed schema fingerprint triggers reindex
- pgvector repository SQL is isolated and testable
- vector and lexical candidates merge without duplicates
- stale schema refs are excluded before prompt context
- low semantic similarity cannot override missing schema coverage
- prompt context remains typed
- default test suite passes without live PostgreSQL, pgvector, OpenAI, or network

Deliverables:
- embedding client implementation and config
- pgvector migration/schema
- embedding indexing service
- pgvector retrieval repository
- hybrid retrieval/reranking service
- typed prompt context integration
- deterministic test suite for semantic retrieval behavior
