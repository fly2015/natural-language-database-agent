# Detailed Implementation Steps

## Week 1: Phase 1 Monolith Delivery

### Step 1: Project Bootstrapping
- Initialize Spring Boot project.
- Add dependencies: LangChain4j, JDBC driver, validation, testing.
- Configure profiles: local, dev.

Deliverable:
- Application starts with health endpoint.

### Step 2: Database and Seed Data
- Setup H2/PostgreSQL schema for demo.
- Add sample business tables (customers, orders, products).
- Seed representative rows.

Deliverable:
- Queryable demo database.

### Step 3: Schema and Rules RAG Index
- Extract schema metadata and business rules.
- Chunk and embed metadata.
- Store in vector index.

Deliverable:
- Retrieval endpoint returning top-k context.

### Step 3A: Retrieval Failure Recovery
- Implement failure detection for: empty retrieval, low confidence, timeout, and repeated validation failure.
- Add bounded retries (max 3 attempts) with strategy progression:
  - attempt 1: normalized intent query
  - attempt 2: synonym/alias-expanded query
  - attempt 3: hybrid retrieval mode
- Add fallback to curated schema dictionary and business-rule cache.
- If confidence is still below threshold, return clarification request instead of SQL execution.

Deliverable:
- Resilient retrieval path with safe-stop behavior and traceable logs.

### Step 3B: Pre-Evaluate RAG Context
- Implement a Context Evaluator before SQL generation.
- Score retrieval context using similarity, schema coverage, join-path readiness, and policy compliance.
- Return one decision: PROCEED, RETRY, or CLARIFY.
- Block SQL generation when decision is CLARIFY.

Deliverable:
- Deterministic pre-LLM quality gate with decision logs.

### Step 4: Prompting and SQL Generation
- Build prompt template for text-to-SQL.
- Include constraints: SELECT only, no mutation, include LIMIT.
- Add English examples.

Deliverable:
- Deterministic SQL generation with test prompts.

### Step 5: Guardrails Engine
- Implement SQL validator:
  - deny write/destructive keywords
  - deny multi-statement SQL
  - enforce SELECT-only
- Implement SQL limiter injector.

Deliverable:
- Guardrail tests passing.

### Step 6: JDBC Execution and Result Formatter
- Execute validated SQL.
- Map result set to JSON/table DTO.
- Add readable markdown table formatter.

Deliverable:
- End-to-end query answer API.

### Step 7: UI Chat Integration
- Build simple chat panel.
- Display answer table and optional generated SQL.
- Show safe error messages.

Deliverable:
- End-user demo flow complete.

### Step 8: Testing and Hardening
- Unit tests for validator and limiter.
- Integration tests for representative business questions.
- Adversarial prompt tests.
- Retrieval failure tests (empty index, timeout, low confidence, schema drift).

Deliverable:
- Test report and known limitations.

## Week 2: Phase 2 MCP Enhancement

### Step 9: Extract MCP-Compatible Services
- Move retrieval and execution into separate service layer.

### Step 10: Implement Java MCP Server
- Publish get_schema_and_rules and execute_select_sql tools.

### Step 11: MCP Client Integration
- Connect Spring Boot client to MCP tools.
- Optional: connect Claude Desktop.

### Step 12: Final Validation
- Compare Phase 1 and Phase 2 outputs and latency.
- Keep monolith fallback switch.

## Acceptance Checklist
- Natural language to SQL works.
- Relevant schema/rules retrieval works.
- Guardrails block non-SELECT SQL.
- Row limits are enforced.
- UI renders tabular output.
- MCP tools operational (phase 2 target).
