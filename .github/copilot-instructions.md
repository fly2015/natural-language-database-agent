# GitHub Copilot Instructions

## Project Context
This project is an Enterprise Natural Language Database Agent.
Primary goal: convert English natural-language questions into safe, accurate, read-only SQL queries and return readable tabular results.

## Primary Stack
- Java 25
- Spring Boot 4.x
- LangChain4j
- PostgreSQL 16+ for production
- H2 for local/demo testing
- Spring JDBC with HikariCP
- pgvector for schema and business-rule retrieval
- REST API for client integration
- Maven for build

## Architecture Expectations
- Phase 1 is a Spring Boot monolith.
- Phase 2 may extract retrieval and execution behind MCP-compatible interfaces.
- Keep business logic modular even in the monolith.
- Prefer clear service boundaries:
  - orchestrator
  - retrieval
  - memory/context resolution
  - typed context validator
  - prompt builder
  - SQL guardrails
  - query executor
  - result formatter
  - audit logger
- Do not mix unrelated responsibilities in one service. Retrieval finds context, generation creates SQL, guardrails validate SQL, execution runs approved SQL only.
- Prefer interface-driven adapters for infrastructure choices such as H2, PostgreSQL, pg_trgm, pgvector, OpenAI, and fake deterministic test providers.

## Core Product Rules
- The system must generate only read-only SQL.
- Never generate or execute INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE, or MERGE.
- Always apply a row limit when generating SQL unless a stricter limit is already present.
- Prefer explicit column lists over SELECT star.
- Use only schema and business-rule context that is explicitly available.
- If context is weak or ambiguous, prefer clarification or safe rejection over guessing.
- Never let the LLM invent schema names, business definitions, join paths, policies, or corrected terms.
- Except for the user input, all SQL-generation context should come from dynamic, externalized, governed, or validated sources.

## Retrieval and Failure Handling
- Retrieval is required before SQL generation when schema context is needed.
- Normalize and correct user input before retrieval.
- User-input correction must use maintained vocabulary, not hardcoded domain literals.
- In H2/default mode, use deterministic in-memory vocabulary and vector adapters.
- In PostgreSQL mode, use `retrieval_vocabulary` with pg_trgm and edit-distance reranking for typo correction.
- Use pgvector or a vector adapter for semantic retrieval of schema chunks, business rules, aliases, and join paths.
- Vector search is a retrieval aid, not the source of truth.
- Source of truth remains live schema metadata, governed business rules, approved join paths, and policy data.
- Keep retrieval context typed: schema, business rule, alias, join path, policy, dialect, and memory where applicable.
- Do not flatten typed context into raw text until the prompt builder formats the final LLM prompt.
- If retrieval fails, follow this recovery order:
  1. retry with normalized query text
  2. retry with expanded terms or aliases
  3. switch to hybrid retrieval
  4. fallback to curated schema or rules cache
  5. ask for clarification and stop safely if confidence remains low
- Do not generate executable SQL when retrieval confidence is below the configured threshold.
- Log retrieval failure codes and attempts for diagnostics.
- Rebuild schema chunks, vocabulary, and embeddings when the schema fingerprint changes.
- Validate business-rule schema references against the active schema before making them prompt context.

## Memory and Context Guidance
- MAG is not implemented by sending full chat history to the LLM.
- MAG must retrieve selected, scoped memory and merge it into working context.
- Keep memory separate from retrieval knowledge:
  - retrieval data: schema, business rules, aliases, join paths
  - memory data: session state, conversation turns, result references, prior task summaries
- Scope memory by tenant, user/role, datasource, and conversation.
- Use recent-window lookup for follow-up references such as "above", "that list", "same filter", and "previous result".
- Store prior results as structured result references, usually entity type and primary keys, not full sensitive rows.
- Ask clarification when a memory reference cannot be resolved safely.

## Coding Guidance
- Prefer simple, testable Spring services over large classes.
- Keep orchestration code separate from SQL validation and database execution.
- Centralize SQL guardrail logic in one service.
- Use immutable DTOs where practical.
- Use constructor injection.
- Avoid hidden side effects.
- Keep method names explicit and domain-based.
- Keep default tests deterministic. Do not require PostgreSQL, pgvector, OpenAI, Docker, or network access for the default test suite.
- Use fake/in-memory adapters for tests and local fallback, but keep production adapters behind the same interfaces.
- Do not hardcode demo domain terms in production retrieval or correction code.

## Database Guidance
- Treat PostgreSQL as the source production target.
- Keep SQL compatible with PostgreSQL unless a file is explicitly for H2 tests.
- Favor curated views for common business questions when they reduce complex joins.
- Validate table and column references before execution.
- Prefer Flyway or Liquibase for production migrations when schema stabilizes.
- Use PostgreSQL pg_trgm for typo/fuzzy vocabulary lookup in production.
- Use PostgreSQL pgvector for production semantic retrieval.
- Keep H2 only for local/demo and deterministic tests.
- Include datasource and tenant fields in governed tables where practical.
- Schema metadata, vocabulary, embeddings, rules, and memory should be refreshable/rebuildable.

## Business Rule Guidance
- Business rules must not be hardcoded in Java production retrieval logic.
- Temporary YAML/config business rules are acceptable for local/demo bootstrap.
- Enterprise business rules should move to governed storage with owner, approval status, version, effective dates, datasource, tenant, aliases, and schema refs.
- Stale rules that reference missing tables or columns must be excluded or downgraded before prompt construction.
- Business-rule embeddings are search indexes, not the source of truth.

## Prompting Guidance
- Keep prompts deterministic and compact.
- Include only the relevant schema and business rules.
- In repair flows, feed back validation errors in structured form.
- If the first generation fails, use bounded retries only.
- After retry limits are exhausted, ask a clarification question instead of forcing SQL.
- Build prompts from typed prompt context, not from untraceable mixed strings.
- The prompt builder may format typed context into text only at the final step.
- Final prompts should include stable instructions, user question, allowed schema, approved business rules, allowed join paths, dialect rules, and strict JSON output contract.
- Do not pass full schema, full memory, or full conversation history when narrower validated context is available.
- Dialect rules must be selected from the active datasource/provider.

## Validation and Guardrail Guidance
- Validate context before generation and validate SQL before execution.
- Typed prompt-context validation should check table refs, column refs, business-rule refs, join paths, policy compatibility, schema fingerprint freshness, dialect, and missing required context.
- SQL guardrails must reject mutation SQL, multiple statements, unknown tables, unknown columns, unsafe limits, and unsupported dialect constructs.
- Repair prompts must include only typed context, active dialect, and structured guardrail errors.
- Generated SQL must always pass centralized guardrails before execution.
- Future hardening should include tenant/security policy checks, row/column access control, SQL parser or AST validation, and query-cost/explain-plan limits.

## API and Output Guidance
- Return structured responses with status, answer, tabular data, SQL, trace ID, and latency when appropriate.
- Error messages must be safe for end users and must not leak secrets or internal configuration.
- Audit logging must capture user question, generated SQL, guardrail decision, and execution outcome.
- Audit logs should also capture retrieval attempts, correction candidates, retrieved chunk ids, rule ids, vector scores, schema fingerprint, prompt version, model, repair attempts, and guardrail reasons where practical.

## Testing Guidance
- Add unit tests for guardrails, limit injection, formatter behavior, and retrieval decision logic.
- Add integration tests for text-to-SQL, validation, and JDBC execution.
- Add adversarial tests for prompt injection and unsafe SQL attempts.
- Add retrieval failure tests for empty results, low confidence, timeout, and schema drift.
- Add tests for typo correction such as transpositions, missing letters, plural/singular, and ambiguous corrections.
- Add tests proving vocabulary and embedding indexes rebuild before correction/retrieval.
- Add tests proving vector and lexical retrieval merge without duplicates.
- Add tests proving stale business rules and stale schema refs do not reach prompt context.
- Use Testcontainers for PostgreSQL, pg_trgm, and pgvector integration tests when adding production-path coverage.

## Documentation Guidance
- Keep documentation in English.
- Preserve numbered document naming in the document folder.
- Align implementation decisions with these existing project docs when relevant:
  - document/01-raw-requirement.md
  - document/03-technology-stack.md
  - document/04-architecture-overview.md
  - document/06-implementation-steps.md
  - document/07-prompt-library.md
  - document/08-retrieval-failure-recovery.md
  - document/09-retrieval-design.md
  - document/note/01-pre-evaluate-rag.md
  - document/note/02-text-normalization-and-typo-correction.md
  - document/note/03-schema-context-and-business-rule-cost.md
  - document/note/04-enterprise-agent-memory-context.md
  - document/note/05-current-retrieval-flow.md
  - document/note/06-mag-implementation-design.md

## When Unsure
- Prefer safety over convenience.
- Prefer explicit clarification over implicit assumptions.
- Prefer maintainable monolith-first code that can later be extracted into MCP-compatible services.
- Prefer clean architecture and well-defined boundaries over fast patches that mix responsibilities.
