---
description: "Implement test coverage, adversarial checks, and hardening for the natural language database agent"
name: "Implement Tests And Hardening"
argument-hint: "Optional focus, such as unit tests, integration tests, retrieval failures, or adversarial cases"
agent: "agent"
model: "GPT-5 (copilot)"
---
Implement the missing test coverage and hardening steps for this project.

Use these documents:
- [Requirements](../../document/01-raw-requirement.md)
- [Implementation Steps](../../document/06-implementation-steps.md)
- [Prompt Library](../../document/07-prompt-library.md)
- [Retrieval Failure Recovery](../../document/08-retrieval-failure-recovery.md)
- [Workspace Copilot Instructions](../copilot-instructions.md)

Task:
- inspect current tests and identify the highest-risk gaps
- implement focused tests for guardrails, retrieval, execution, and formatting
- add adversarial and failure-path coverage before widening to lower-risk areas

Required test categories:
- SQL mutation and multi-statement rejection
- retrieval empty result and low-confidence handling
- timeout and fallback behavior
- invalid schema reference handling
- end-to-end happy path for a representative business query

Constraints:
- prefer narrow, deterministic tests
- use H2 only where appropriate for local integration coverage
- keep tests aligned with PostgreSQL behavior whenever SQL semantics matter
- summarize remaining risk after validation
