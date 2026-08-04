---
description: "Implement SQL generation, SQL guardrails, and safe JDBC execution for the agent"
name: "Implement SQL Guardrails"
argument-hint: "Optional focus, such as validator, limiter, execution service, or SQL repair flow"
agent: "agent"
model: "GPT-5 (copilot)"
---
Implement the SQL generation and execution safety path for this project.

Use these documents:
- [Requirements](../../document/01-raw-requirement.md)
- [Technology Stack](../../document/03-technology-stack.md)
- [Architecture Overview](../../document/04-architecture-overview.md)
- [Implementation Steps](../../document/06-implementation-steps.md)
- [Prompt Library](../../document/07-prompt-library.md)
- [Workspace Copilot Instructions](../copilot-instructions.md)

Task:
- Implement prompt-to-SQL generation flow.
- Implement a centralized SQL guardrail service.
- Implement safe JDBC execution for approved SELECT statements only.

Guardrail requirements:
- allow only one SELECT statement
- reject INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE, MERGE
- reject multi-statement SQL
- enforce row limit when missing
- prefer explicit columns over SELECT star where practical
- never execute SQL if validation fails

Validation workflow:
1. generate SQL candidate
2. validate SQL
3. repair only within bounded retry rules if needed
4. execute only if approved
5. return safe end-user response and audit data

Tests to add:
- mutation query rejection
- multi-statement rejection
- limit injection
- valid SELECT execution path
- invalid table or column handling
