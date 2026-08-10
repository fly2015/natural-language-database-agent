# Current Retrieval Flow Note

## Purpose
Summarize the current agent flow from user input to trusted retrieval context.

## Flow
```text
1. User asks question
```

```text
2. Prepare indexes
SchemaRetriever.prepare()
-> SchemaIndexService.currentChunks()
-> extract live schema metadata
-> build schema chunks
-> build business-rule chunks
-> build join-path chunks
-> rebuild vocabulary
-> rebuild embeddings
```

```text
3. Normalize and correct input
RetrievalQueryProcessor.process(question)
-> TextNormalizer normalizes text
-> VocabularyCorrectionService corrects typos
   - H2/default: in-memory vocabulary
   - Postgres: retrieval_vocabulary + pg_trgm + edit-distance rerank
-> produce ProcessedQuery
```

```text
4. Retrieve context
DynamicSchemaRetriever.retrieve(processedQuery)
-> lexical/fuzzy scoring over typed chunks
-> semantic vector search
   - H2/default: in-memory vector store
   - Postgres: pgvector
-> merge results by chunk id
-> rerank
```

```text
5. Evaluate retrieval
RetrievalService
-> calculate confidence
-> check typed context coverage
-> proceed, retry, fallback, or clarify
```

```text
6. Send trusted context to SQL generation
Only trusted retrieved snippets continue to prompt and SQL generation.
```

## Current Enterprise Pieces
- dynamic schema extraction
- external business rules
- typed chunks: schema, business rule, join path
- typo correction before retrieval
- in-memory and PostgreSQL vocabulary adapters
- embedding indexing
- in-memory and pgvector retrieval adapters
- schema drift handling through fingerprint rebuild
- low-confidence clarification path

## Enterprise Maturity
Current status:

```text
Architecture direction: enterprise-aligned
Current implementation: strong prototype / early enterprise foundation
Full enterprise standard: not yet
```

Already strong:
- dynamic schema extraction
- typed retrieval chunks
- externalized business rules
- input normalization before retrieval
- typo correction before retrieval
- pg_trgm design for typo search
- embedding indexing
- in-memory and pgvector vector paths
- schema fingerprint rebuild
- confidence gate before SQL generation
- deterministic local tests

Still needed for full enterprise standard:
- move business rules to governed DB tables
- add approval, version, owner, and effective-date lifecycle for rules
- add admin/API workflow for refresh, rebuild, and rule management
- add stronger multi-tenant and datasource isolation
- add Flyway or Liquibase migrations
- add real PostgreSQL integration tests, preferably with Testcontainers
- make scoring weights and thresholds configurable
- pass structured typed prompt context, not only snippets
- improve audit logs for chunk ids, vector scores, correction candidates, and schema fingerprint
- implement enterprise memory/context later

## Generation And Guardrail Flow
```text
trusted retrieval context
-> prompt builder
-> LLM or deterministic SQL generation
-> SQL guardrail validation
-> bounded repair when allowed
-> execute only if guardrail allows
```

Already good:
- SQL generation is behind an interface
- deterministic/mock generation exists for local tests
- generated SQL is validated before execution
- write/destructive SQL is rejected
- unknown tables and columns are rejected
- bounded repair exists
- dialect-aware prompting note exists
- audit logging captures question, SQL, decision, and outcome

Still needed for full enterprise standard:
- prompt context should be fully typed, not only snippet text
- SQL dialect handling should be provider-driven and test-covered
- guardrail should validate against active datasource metadata and policy
- repair should use structured errors and typed context only
- add tenant/security policy checks
- add row-level and column-level access control
- add stronger LLM cost, timeout, and resource controls
- add explain-plan or query-cost guardrail
- strengthen SQL parser or AST validation if current validation is too shallow
- production audit should include retrieved chunk ids, rule ids, prompt version, model, repair attempts, and guardrail reasons

Current status:

```text
Retrieval: strongest and most recently improved
Generation: good foundation
Guardrails: necessary baseline exists
Full enterprise standard: needs stronger policy, dialect, typed context, audit, and query-cost controls
```
