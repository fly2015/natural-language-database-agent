---
description: "Replace deterministic SQL mapping with LangChain4j-backed text-to-SQL generation and repair flow"
name: "Implement LangChain4j SQL Generation"
argument-hint: "Optional focus, such as provider config, prompt builder, JSON parsing, repair retry, or mock LLM tests"
agent: "agent"
model: "GPT-5 (copilot)"
---
Implement real LLM-backed SQL generation for the natural language database agent using LangChain4j.

Use these documents:
- [Requirements](../../document/01-raw-requirement.md)
- [Technology Stack](../../document/03-technology-stack.md)
- [Architecture Overview](../../document/04-architecture-overview.md)
- [Implementation Steps](../../document/06-implementation-steps.md)
- [Prompt Library](../../document/07-prompt-library.md)
- [Retrieval Failure Recovery](../../document/08-retrieval-failure-recovery.md)
- [Workspace Copilot Instructions](../copilot-instructions.md)

Task:
- Replace or extend the current deterministic `SqlGenerationService` phrase mapping with a LangChain4j-backed SQL generation flow.
- Build prompts from retrieved schema chunks and business rules only.
- Parse the LLM response as structured JSON with status, SQL, assumptions, and rejection reason.
- Keep deterministic/mock LLM support for local tests.

Required behavior:
- generate exactly one read-only `SELECT` statement when context is sufficient
- never generate or execute write/destructive SQL
- include `LIMIT 100` unless the user requests fewer rows
- prefer explicit column lists over `SELECT *`
- reject unsupported or ambiguous questions safely
- run generated SQL through the existing centralized guardrail service before execution
- repair invalid SQL only with bounded retries using structured guardrail/schema validation errors
- after retry limits are exhausted, ask for clarification instead of forcing SQL

Implementation constraints:
- keep provider configuration environment-driven; do not hardcode API keys
- keep LLM provider code behind a small interface so tests can use a fake model
- do not send full database schema when retrieval provides narrower context
- do not leak prompts, secrets, stack traces, or internal config to end users
- preserve the existing REST response shape
- preserve audit logging for question, generated SQL, guardrail decision, and execution outcome

Tests to add:
- fake LLM happy path returns valid SQL and executes through JDBC
- mutation SQL returned by fake LLM is rejected and never executed
- multi-statement SQL returned by fake LLM is rejected
- invalid table/column response triggers bounded repair
- repair success executes only the approved repaired SQL
- repair exhaustion returns safe clarification/rejection
- malformed LLM JSON returns safe rejection
- prompt includes only retrieved context snippets and business rules

Deliverables:
- prompt builder service
- LangChain4j SQL generator adapter
- structured LLM response parser
- bounded SQL repair flow
- fake/mock LLM tests for deterministic CI
