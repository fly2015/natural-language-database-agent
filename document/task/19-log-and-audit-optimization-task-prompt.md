# Task Prompt: Log And Audit Optimization

## Source
- Related embedding/indexing task: `document/task/18-openai-embedding-indexing-process-task-prompt.md`
- Current audit/logging files:
  - `src/main/java/com/nlda/audit/AuditEvent.java`
  - `src/main/java/com/nlda/audit/AuditLogger.java`
  - `src/main/java/com/nlda/audit/AuditStep.java`
  - `src/main/java/com/nlda/audit/AuditContext.java`
  - `src/main/java/com/nlda/orchestrator/AgentOrchestrator.java`
  - `src/main/java/com/nlda/retrieval/RetrievalService.java`
  - `src/main/java/com/nlda/retrieval/impl/retriever/DynamicSchemaRetriever.java`
  - `src/main/java/com/nlda/generation/SqlGenerationService.java`
  - `src/main/java/com/nlda/guardrail/SqlGuardrailService.java`
  - `src/main/java/com/nlda/execution/QueryExecutor.java`
  - `src/main/java/com/nlda/format/ResultFormatter.java`
  - `src/main/java/com/nlda/retrieval/index/EmbeddingIndexService.java`
  - `src/main/java/com/nlda/retrieval/index/SchemaIndexService.java`
  - `src/main/java/com/nlda/retrieval/governance/RetrievalIndexRebuildService.java`

## Objective
Optimize logging and audit behavior so audit remains simple, stable, and safe, while detailed request flow and operational diagnostics are emitted through normal application loggers.

Audit should answer "what happened to this user request?" at a compact business/security level. Application logs should answer "how did each pipeline step behave?" for debugging, monitoring, dashboards, alerts, and later distributed tracing.

## Required Outcome
- Audit events are small and similar to the original simple audit shape.
- Audit does not contain large nested step payloads, retrieved snippets, table rows, full LLM prompts, full model responses, or large result bodies.
- Detailed per-step request flow is logged through SLF4J component loggers with a consistent key/value format.
- Every request flow log includes `traceId`.
- Major pipeline steps log start/end/failure with stable event names.
- Embedding indexing, retrieval, SQL generation, guardrail validation, database execution, and response formatting logs are standardized enough for monitoring and trace correlation.
- Sensitive values are never logged:
  - API keys
  - authorization headers
  - raw environment variables containing secrets
  - full prompts if they may contain private schema or user-sensitive content
  - full result rows by default
- Existing tests continue to pass, and new tests cover compact audit output and traceable flow logs where practical.

## Implementation Prompt
You are working in the `natural-language-database-agent` repository. Refactor audit and operational logging so detailed pipeline diagnostics move out of the audit JSON and into standardized component logs.

Keep the implementation lightweight. Do not introduce OpenTelemetry, Micrometer tracing, log aggregation agents, or a new persistence store unless explicitly requested later. Use the existing Spring Boot and SLF4J logging patterns.

## Current Problem
The current audit event can become too large because `AgentOrchestrator` appends detailed step data to `AuditEvent`, including retrieval snippets, generated SQL data, guardrail details, table output, and response data. This makes audit noisy and harder to use as a stable security/business record.

At the same time, detailed logs are distributed across components with inconsistent event names and fields. This makes it harder to follow a request by `traceId`, monitor step latency, and diagnose failures.

## Target Design

Audit log:

```text
auditEvent traceId=<trace-id> status=<OK|REJECTED|ERROR> latencyMs=<ms> question="<sanitized-short-question>" sqlHash=<hash-or-empty> guardrailDecision=<ALLOW|DENY|SKIPPED> reason="<short-reason>"
```

Flow logs:

```text
flowEvent=<stable.event.name> traceId=<trace-id> status=<STARTED|OK|REJECTED|FAILED|SKIPPED> latencyMs=<ms> key=value ...
```

Indexing and admin operational logs:

```text
operationEvent=<stable.event.name> status=<OK|FAILED|SKIPPED> key=value ...
```

## Functional Requirements

1. Compact audit model
   - Replace or supplement the current large audit JSON with a compact audit record.
   - Keep only stable top-level fields:
     - `traceId`
     - `startedAt`
     - `status`
     - `durationMs`
     - sanitized `question`
     - `sqlHash` or short SQL summary, not full SQL by default
     - `guardrailDecision`
     - `rowCount`
     - `reason`
   - Do not store nested `steps` in the final audit event by default.
   - If step collection is retained for test compatibility, do not serialize it in normal audit logs.

2. Request trace context
   - Ensure every request has a `traceId`.
   - Make `traceId` available to lower-level services through one of:
     - existing `AuditContext`
     - a small `TraceContext`
     - SLF4J MDC
   - Prefer MDC if it can be introduced cleanly, because it supports standard log correlation.
   - Clear context in `finally` blocks to avoid leaking IDs between requests.

3. Standard flow log events
   - Add or standardize logs for:
     - `request.received`
     - `retrieval.started`
     - `retrieval.query_processed`
     - `retrieval.semantic_search`
     - `retrieval.completed`
     - `llm.sql_generation.started`
     - `llm.sql_generation.completed`
     - `guardrail.validation.completed`
     - `database.execution.completed`
     - `response.format.completed`
     - `request.completed`
   - Use `INFO` for normal start/end events.
   - Use `WARN` for rejected or recoverable failures.
   - Use `ERROR` only for unexpected exceptions or infrastructure failures.

4. Log field standards
   - Use stable fields consistently:
     - `traceId`
     - `flowEvent` or `operationEvent`
     - `status`
     - `latencyMs`
     - `provider`
     - `model`
     - `mode`
     - `confidence`
     - `failureCode`
     - `resultCount`
     - `chunkCount`
     - `rowCount`
     - `reason`
   - Avoid large or unstable fields:
     - full prompt
     - full retrieved snippets
     - full result rows
     - full embeddings
     - raw API response bodies
   - Use IDs and counts instead:
     - selected chunk IDs
     - top chunk count
     - row count
     - SQL hash
     - content hash

5. Retrieval logs
   - `RetrievalService` should log each attempt with `traceId`, mode, confidence, failure code, and result count.
   - `DynamicSchemaRetriever` should log semantic search separately:
     - skipped because no embedding client
     - skipped because no active embeddings
     - query embedding failure
     - vector search failure
     - success with result count and top chunk IDs
   - Logs should make it clear whether pgvector semantic retrieval was used or lexical-only retrieval was used.

6. SQL generation logs
   - `SqlGenerationService` should log provider/model, phase, status, latency, and reason.
   - Do not log the full prompt by default.
   - Do not log raw LLM output by default.
   - Log generated SQL only as a hash or short sanitized summary unless debug logging is explicitly enabled.

7. Guardrail logs
   - `SqlGuardrailService` should log allow/deny decisions with:
     - `traceId`
     - `status`
     - SQL hash
     - violation count
     - violation codes or short messages
   - Avoid full SQL at info level.

8. Database execution logs
   - `QueryExecutor` should log:
     - database identifier or safe JDBC target summary
     - SQL hash
     - status
     - latency
     - row count
   - Do not log full row data.

9. Indexing and rebuild logs
   - Keep embedding and index rebuild logs outside user audit.
   - Standardize operational logs for:
     - schema index build/rebuild
     - vocabulary rebuild
     - embedding rebuild
     - chunk embedding failure
     - retrieval index admin rebuild
   - Include provider/model/fingerprint/chunk counts/failure counts.
   - Do not log embedding vectors or API keys.

10. Configuration
   - Add properties if useful:

```yaml
agent:
  audit:
    include-step-details: false
    include-sql: false
  logging:
    flow-enabled: true
    include-debug-payloads: false
```

   - Defaults should be safe and compact.
   - Debug payload logging, if supported, must require explicit configuration.

11. Tests
   - Add or update tests for:
     - audit log does not include full step payloads by default
     - audit log includes `traceId`, status, latency, sanitized question, row count, and reason
     - flow logs include `traceId` for request/retrieval/generation/guardrail/execution steps
     - SQL/prompt/result rows are not logged at info level by default
     - semantic retrieval logs indicate skipped/success/failure state
   - Keep tests practical. Prefer unit tests around audit serialization and focused logging behavior.

## Suggested Implementation Steps

1. Inventory current audit payload usage
   - Inspect `AgentOrchestrator`, `AuditEvent`, `AuditLogger`, and `AuditStep`.
   - Identify which data should remain in audit and which data should move to component logs.

2. Introduce a compact audit record
   - Create a DTO such as `AuditSummary`.
   - Update `AuditLogger.record(...)` to emit compact audit data.
   - Keep old method signatures only if they reduce churn, but avoid serializing large fields.

3. Add trace context helper
   - Reuse `AuditContext` or introduce MDC.
   - Ensure lower-level services can include `traceId` without passing it through every method signature.

4. Move detailed step logging into component loggers
   - Add flow logs in orchestrator boundaries.
   - Add detail logs inside retrieval, semantic search, SQL generation, guardrail, execution, and formatting.

5. Standardize event names and fields
   - Use `flowEvent=...` for user request path.
   - Use `operationEvent=...` for indexing/admin/background operations.
   - Keep field names stable across classes.

6. Redact and summarize
   - Add small helpers for:
     - SQL hash
     - sanitized short question
     - selected chunk IDs
     - safe JDBC/database name
     - truncated reason text
   - Do not log secrets or large payloads.

7. Update tests
   - Adjust tests expecting detailed audit JSON.
   - Add tests that prove compact audit and traceable flow logs.

8. Validate with a running request
   - Run the app.
   - Execute:

```powershell
$queryBody = @'
{"question":"list 10 customers"}
'@

$queryBody | curl.exe -X POST http://localhost:8080/api/query `
  -H "Content-Type: application/json" `
  --data-binary '@-'
```

   - Inspect logs and confirm:
     - one compact `auditEvent`
     - multiple detailed `flowEvent` logs with the same `traceId`
     - no full rows, full prompts, API keys, or embeddings in info logs

## Non-Goals
- Do not add a new tracing platform.
- Do not add a log aggregation dependency.
- Do not persist full audit step details to a database.
- Do not log full OpenAI prompts or responses by default.
- Do not log embeddings or pgvector values.
- Do not change business behavior, retrieval ranking, SQL generation, guardrails, or database results except as needed to pass trace IDs through logging.

## Acceptance Criteria
- Audit logs are compact and no longer include large nested per-step payloads by default.
- Detailed request flow is visible through standardized `flowEvent` logs.
- All request-path logs can be correlated by the same `traceId`.
- Semantic retrieval logs show whether OpenAI embeddings and pgvector were used, skipped, or failed.
- SQL generation, guardrail, database execution, and response formatting logs include status and latency.
- Info-level logs do not include API keys, embeddings, full prompts, full result rows, or full LLM responses.
- Rebuild/indexing logs use standardized `operationEvent` names and include provider/model/fingerprint/counts.
- Tests pass with JDK 25.

## Validation Commands

Run focused audit/logging tests:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace eclipse-temurin:25-jdk ./mvnw -q "-Dtest=*Audit*,*Orchestrator*,*Retrieval*" test
```

Run the full test suite:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace eclipse-temurin:25-jdk ./mvnw test
```

Start PostgreSQL stack:

```powershell
docker compose up -d --build
```

Run a query:

```powershell
$queryBody = @'
{"question":"list 10 customers"}
'@

$queryBody | curl.exe -X POST http://localhost:8080/api/query `
  -H "Content-Type: application/json" `
  --data-binary '@-'
```

Inspect recent app logs:

```powershell
docker logs --tail 200 nlda-app-full-postgres
```

Check that logs contain:

```text
auditEvent
flowEvent=request.received
flowEvent=retrieval.started
flowEvent=retrieval.completed
flowEvent=llm.sql_generation.completed
flowEvent=guardrail.validation.completed
flowEvent=database.execution.completed
flowEvent=request.completed
```

Check that logs do not contain:

```text
OPENAI_API_KEY
Authorization: Bearer
embedding=[
full prompt text
full table rows
```
