---
description: "Implement application logging to local log files with safe rotation and audit traceability"
name: "Implement File Logging"
argument-hint: "Optional focus, such as Logback config, audit logs, JSON logs, rotation, or test verification"
agent: "agent"
model: "GPT-5 (copilot)"
---
Implement file-based logging for the natural language database agent.

Use these documents:
- [Technology Stack](../../document/03-technology-stack.md)
- [Architecture Overview](../../document/04-architecture-overview.md)
- [Implementation Steps](../../document/06-implementation-steps.md)
- [Workspace Copilot Instructions](../copilot-instructions.md)

Task:
- Configure the application to write logs to local files in addition to console output.
- Include operational logs and audit-relevant logs with trace IDs.
- Keep log output safe: never log secrets, credentials, API keys, raw stack traces to users, or sensitive database values beyond approved audit metadata.

Required behavior:
- write application logs to a file under a configurable log directory
- use rolling log files with size and retention limits
- include timestamp, level, logger, thread, trace ID when available, and message
- preserve existing console logging for local development
- ensure audit logs include user question, generated SQL, guardrail decision, execution outcome, and trace ID
- keep end-user API errors safe while internal errors are logged for diagnostics

Implementation constraints:
- prefer Logback configuration because Spring Boot uses it by default
- make log directory configurable by environment/property
- use profile-aware configuration when useful, for example plain local logs and JSON-like structured logs for non-local environments
- do not hardcode absolute machine-specific paths
- do not add a heavy logging dependency unless clearly needed
- keep changes compatible with Java 21 and Spring Boot 4.x

Tests or validation to add:
- application starts with file logging configuration
- log directory can be overridden through configuration
- audit logger emits trace ID and guardrail decision
- API unexpected errors return safe responses while logging diagnostic details internally
- generated log files are ignored by Git

Deliverables:
- Logback or Spring logging configuration
- configurable log path properties
- `.gitignore` updates for generated log files
- focused tests or documented validation commands
- summary of log file location, rotation policy, and remaining operational risks
