# Architecture Overview (Phase 1 First)

## 1. Strategy
Use phased delivery:
- Phase 1: Reliable monolith for guaranteed working demo.
- Phase 2: MCP refactor for modularity and plug-and-play interoperability.

## 2. Phase 1: Spring Boot Monolith (Primary)

### Components
- Web UI Chat Interface
- Agent Orchestrator Service
- RAG Retriever Service
- Pre-LLM Context Evaluator
- Prompt Builder Service
- Post-LLM SQL Validation Gate
- SQL Guardrails Service
- JDBC Execution Service
- Result Formatter Service
- Audit Logger

### Data Flow
1. UI sends question to orchestrator.
2. Orchestrator retrieves relevant schema and business rules.
3. Pre-LLM Context Evaluator scores retrieval quality and decides proceed/retry/clarify.
4. Prompt builder composes context for LLM only when retrieval confidence is sufficient.
5. LLM returns SQL candidate.
6. Post-LLM SQL Validation Gate checks syntax, schema references, and safety compliance.
7. Guardrails validate and rewrite (add LIMIT if needed).
8. JDBC executes query.
9. Formatter transforms rows into table response.
10. UI renders answer.

### Why this works first
- Fastest path to a complete system.
- Lower integration complexity.
- Easier debugging and demonstration.

## 3. Key Design Contracts
- IAgentOrchestrator: coordinates pipeline.
- IRagRetriever: returns top-k schema/rule chunks.
- ISqlGuardrail: validates and sanitizes SQL.
- IQueryExecutor: executes approved SQL only.
- IResultFormatter: structured response model.

## 4. Storage and Indexing
- Relational DB for business data.
- Vector store for schema and rule embeddings.
- Optional in-memory cache for repeated retrieval requests.

## 5. Operational Controls
- Query timeout.
- Max rows default: 100.
- Max rows hard cap: 1000.
- Request tracing ID for each query path.
- Structured logs for audit and troubleshooting.

## 6. Risks and Mitigation
- Risk: Hallucinated columns/tables.
  - Mitigation: strict schema retrieval and validator checks.
- Risk: Expensive broad queries.
  - Mitigation: automatic limits and timeout.
- Risk: Security bypass attempts.
  - Mitigation: denylist + AST/regex validation + single statement rule.

## 7. Retrieval Failure Handling
- Add a Retrieval Recovery Controller in the orchestrator path.
- Failure classification codes: RF-01 (no result), RF-02 (low confidence), RF-03 (timeout/unavailable), RF-04 (post-retrieval validation loop failure).
- Recovery order:
  1. Re-query with normalized intent.
  2. Re-query with expanded terms.
  3. Switch to hybrid retrieval.
  4. Fallback to curated schema/rules cache.
  5. Ask targeted clarification and fail safely when below confidence threshold.
- Store attempt metadata in audit logs for tuning and governance.

Reference: see 08-retrieval-failure-recovery.md for operational details.

## 8. Context Evaluation Policy
- Evaluate retrieval context before SQL generation using deterministic scoring.
- Use a confidence threshold to decide:
  - proceed to LLM
  - retry retrieval with stronger strategies
  - ask clarification and safe stop
- Recommended score dimensions:
  - semantic similarity strength
  - schema coverage for required entities/metrics/time filters
  - join-path availability between referenced entities
  - policy compliance for allowed schemas
  - business-rule consistency
