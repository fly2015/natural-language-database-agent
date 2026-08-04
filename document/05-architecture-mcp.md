# MCP Architecture (Phase 2 Enhancement)

## 1. Objective
Refactor database intelligence into an independent MCP Server so any MCP client can reuse the same secure tools.

## 2. Topology
- MCP Client:
  - Spring Boot Web UI client, or
  - Claude Desktop (or other MCP-compatible host)
- Java Database MCP Server:
  - Tool A: get_schema_and_rules
  - Tool B: execute_select_sql
- Database via JDBC

## 3. MCP Tool Specifications

### Tool: get_schema_and_rules
Input:
- user_question
- optional domain hint

Output:
- relevant_tables
- relevant_columns
- business_rules
- retrieval_confidence

Behavior:
- Retrieve top-k schema/rule chunks.
- Return compact context to reduce tokens.

### Tool: execute_select_sql
Input:
- sql_query
- requester_context

Output:
- execution_status
- row_count
- data_rows
- execution_time_ms
- error_message (if failed)

Behavior:
- Validate read-only constraints.
- Inject LIMIT if missing.
- Execute through JDBC with timeout.

## 4. MCP Benefits
- Reusability across clients.
- Separation of concerns and maintainability.
- Easier independent scaling and testing.
- Standards-based integration.

## 5. Migration Plan from Monolith to MCP
1. Extract RAG module behind interface.
2. Extract guardrails module behind interface.
3. Expose modules as MCP tools.
4. Replace direct in-process calls with MCP client calls.
5. Run compatibility tests and benchmark latency.

## 6. Backward Compatibility
Keep monolith mode as fallback in case MCP server is unavailable during demo period.
