---
description: "Implement the REST API and simple chat UI for the natural language database agent"
name: "Implement API And UI"
argument-hint: "Optional focus, such as REST DTOs, controller, result formatting, or chat page"
agent: "agent"
model: "GPT-5 (copilot)"
---
Implement the user-facing API and MVP UI for this project.

Use these documents:
- [Requirements](../../document/01-raw-requirement.md)
- [Technology Stack](../../document/03-technology-stack.md)
- [Architecture Overview](../../document/04-architecture-overview.md)
- [Implementation Steps](../../document/06-implementation-steps.md)
- [Workspace Copilot Instructions](../copilot-instructions.md)

Task:
- Implement a REST endpoint for natural-language queries.
- Return structured response data including status, answer, table, SQL, trace ID, and latency when appropriate.
- Build a simple Spring MVC or Thymeleaf chat interface for MVP usage.

Constraints:
- keep error messages safe and user-friendly
- show tabular output clearly
- optionally expose generated SQL in a transparent debug panel
- keep UI simple and maintainable

Required output structure:
- controller and DTOs
- service integration with orchestrator
- formatter for table response
- basic UI template if not already present
- focused tests for controller and response format
