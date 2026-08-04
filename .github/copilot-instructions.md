# GitHub Copilot Instructions

## Project Context
This project is an Enterprise Natural Language Database Agent.
Primary goal: convert English natural-language questions into safe, accurate, read-only SQL queries and return readable tabular results.

## Primary Stack
- Java 21
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
  - prompt builder
  - SQL guardrails
  - query executor
  - result formatter
  - audit logger

## Core Product Rules
- The system must generate only read-only SQL.
- Never generate or execute INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE, or MERGE.
- Always apply a row limit when generating SQL unless a stricter limit is already present.
- Prefer explicit column lists over SELECT star.
- Use only schema and business-rule context that is explicitly available.
- If context is weak or ambiguous, prefer clarification or safe rejection over guessing.

## Retrieval and Failure Handling
- Retrieval is required before SQL generation when schema context is needed.
- If retrieval fails, follow this recovery order:
  1. retry with normalized query text
  2. retry with expanded terms or aliases
  3. switch to hybrid retrieval
  4. fallback to curated schema or rules cache
  5. ask for clarification and stop safely if confidence remains low
- Do not generate executable SQL when retrieval confidence is below the configured threshold.
- Log retrieval failure codes and attempts for diagnostics.

## Coding Guidance
- Prefer simple, testable Spring services over large classes.
- Keep orchestration code separate from SQL validation and database execution.
- Centralize SQL guardrail logic in one service.
- Use immutable DTOs where practical.
- Use constructor injection.
- Avoid hidden side effects.
- Keep method names explicit and domain-based.

## Database Guidance
- Treat PostgreSQL as the source production target.
- Keep SQL compatible with PostgreSQL unless a file is explicitly for H2 tests.
- Favor curated views for common business questions when they reduce complex joins.
- Validate table and column references before execution.

## Prompting Guidance
- Keep prompts deterministic and compact.
- Include only the relevant schema and business rules.
- In repair flows, feed back validation errors in structured form.
- If the first generation fails, use bounded retries only.
- After retry limits are exhausted, ask a clarification question instead of forcing SQL.

## API and Output Guidance
- Return structured responses with status, answer, tabular data, SQL, trace ID, and latency when appropriate.
- Error messages must be safe for end users and must not leak secrets or internal configuration.
- Audit logging must capture user question, generated SQL, guardrail decision, and execution outcome.

## Testing Guidance
- Add unit tests for guardrails, limit injection, formatter behavior, and retrieval decision logic.
- Add integration tests for text-to-SQL, validation, and JDBC execution.
- Add adversarial tests for prompt injection and unsafe SQL attempts.
- Add retrieval failure tests for empty results, low confidence, timeout, and schema drift.

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

## When Unsure
- Prefer safety over convenience.
- Prefer explicit clarification over implicit assumptions.
- Prefer maintainable monolith-first code that can later be extracted into MCP-compatible services.
