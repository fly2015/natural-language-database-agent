# Instruction Guide: Enterprise Natural Language Database Agent

## 1. Goal
Build a production-ready Natural Language to SQL agent that lets business users ask database questions in natural language and receive safe, accurate, readable results.

## 2. Delivery Scope
- Week 1: Monolith architecture using Spring Boot + LangChain4j + JDBC + RAG + guardrails.
- Week 2: MCP architecture upgrade with a standalone Java MCP Server and MCP-compatible client integration.

## 3. Success Criteria
- Converts natural language questions into valid SQL SELECT statements.
- Uses retrieval to inject only relevant schema and business rules.
- Blocks all write/destructive SQL operations.
- Applies automatic row limiting to avoid heavy queries.
- Returns results in a readable table format.
- Demonstrates both monolith and MCP-compatible designs.

## 4. Mandatory Functional Requirements
- English natural-language question support.
- Schema-aware query generation.
- Business rule-aware query generation.
- Read-only execution pipeline.
- Guardrails validation before database execution.
- Clear error handling for invalid or unsupported requests.

## 5. Mandatory Non-Functional Requirements
- Secure by default: hard deny for UPDATE, INSERT, DELETE, DROP, ALTER, TRUNCATE, CREATE.
- Stable runtime under malformed user input.
- Query timeout and row-limit controls.
- Audit logging for user input, generated SQL, and execution outcome.
- Cost control via selective retrieval.

## 6. Recommended Tech Stack
- Language: Java 21+
- Framework: Spring Boot
- Agent SDK: LangChain4j
- Database: PostgreSQL (primary), H2 (local demo)
- Embeddings/LLM: OpenAI or Gemini or Claude
- Build: Maven or Gradle

## 7. Runtime Workflow
1. User submits a natural-language query.
2. Intent and entity extraction identifies target data domains.
3. RAG retrieves relevant schema chunks and business rules.
4. LLM generates SQL draft.
5. Guardrails validate SQL and enforce limits.
6. JDBC executes approved SQL.
7. Backend formats result into table/markdown.
8. UI displays answer with optional SQL transparency panel.

## 8. Security Rules
- Allow only single SELECT statement.
- Deny SQL containing semicolon chains, comments-based bypass patterns, or write keywords.
- Deny access to restricted schemas/tables if configured.
- Force LIMIT when missing.
- Optionally enforce ORDER BY for deterministic previews.

## 9. Testing Requirements
- Unit tests for SQL validator and limiter.
- Integration tests for text-to-SQL and JDBC execution.
- Adversarial tests for prompt injection and SQL injection patterns.
- Performance tests for retrieval precision and latency.

## 10. Definition of Done
- End-to-end demo from natural language question to tabular answer.
- Guardrails pass/fail evidence with test cases.
- Documentation for architecture, implementation steps, and prompts.
- Optional MCP phase operational with at least two tools:
  - get_schema_and_rules
  - execute_select_sql
