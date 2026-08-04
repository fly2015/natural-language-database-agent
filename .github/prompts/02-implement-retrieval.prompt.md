---
description: "Implement schema retrieval, business-rule retrieval, and retrieval failure recovery for the agent"
name: "Implement Retrieval Layer"
argument-hint: "Optional retrieval scope, such as pgvector, fallback cache, or confidence scoring"
agent: "agent"
model: "GPT-5 (copilot)"
---
Implement the retrieval layer for the natural language database agent.

Use these documents:
- [Technology Stack](../../document/03-technology-stack.md)
- [Architecture Overview](../../document/04-architecture-overview.md)
- [Implementation Steps](../../document/06-implementation-steps.md)
- [Prompt Library](../../document/07-prompt-library.md)
- [Retrieval Failure Recovery](../../document/08-retrieval-failure-recovery.md)
- [Workspace Copilot Instructions](../copilot-instructions.md)

Task:
- Build or extend schema and business-rule retrieval.
- Implement top-k retrieval, confidence scoring, and retrieval failure handling.
- Add bounded retries and fallback behavior according to the recovery playbook.

Required behavior:
- classify retrieval failures using RF-01 to RF-04
- retry with normalized query text
- retry with expanded terms or aliases
- switch to hybrid retrieval when needed
- fallback to curated schema or business-rule cache
- stop safely and request clarification if confidence remains too low

Implementation constraints:
- keep retrieval interfaces modular
- log attempt count, mode, and confidence summary
- add tests for empty retrieval, timeout, low confidence, and schema drift

Deliverables:
- retrieval service or module
- failure recovery logic
- tests proving safe behavior
