---
description: "Implement the Phase 1 Spring Boot monolith for the natural language database agent"
name: "Implement Phase 1 Monolith"
argument-hint: "Optional focus area, such as bootstrapping, retrieval, or guardrails"
agent: "agent"
model: "GPT-5 (copilot)"
---
Implement Phase 1 of this project as a Spring Boot 4.x monolith.

Use these project documents as the source of truth:
- [Requirements](../../document/01-raw-requirement.md)
- [Technology Stack](../../document/03-technology-stack.md)
- [Architecture Overview](../../document/04-architecture-overview.md)
- [Implementation Steps](../../document/06-implementation-steps.md)
- [Prompt Library](../../document/07-prompt-library.md)
- [Retrieval Failure Recovery](../../document/08-retrieval-failure-recovery.md)
- [Workspace Copilot Instructions](../copilot-instructions.md)

Task:
- Inspect the current workspace and determine what already exists.
- Implement the next missing part of the Phase 1 monolith.
- Prefer the smallest complete vertical slice that produces a runnable improvement.
- If the project is not initialized yet, scaffold the backend foundation first.

Mandatory constraints:
- Use Java 21, Spring Boot 4.x, Maven, LangChain4j, PostgreSQL-targeted SQL, and H2 for local tests.
- Keep business logic modular inside the monolith.
- Enforce read-only SQL rules.
- Never introduce write-query execution paths.
- Add or update focused tests for each change.

Expected execution order:
1. verify current project state
2. choose the next missing implementation slice
3. implement code
4. validate with focused tests or build checks
5. summarize what remains for the next slice

If an argument is supplied, prioritize that area while still following the project documents.
