# Technology Stack Definition

## 1. Purpose
This document defines the technologies selected for the Enterprise Natural Language Database Agent and why they are used.

## 2. Core Principles for Selection
- Enterprise stability and long-term support.
- Fast delivery for MVP and clear upgrade path.
- Security-first for database access.
- Interoperability for future MCP-based architecture.

## 3. Selected Technologies

### 3.1 Backend Application
- Java 21
- Spring Boot 4.x

Why:
- Mature enterprise ecosystem.
- Strong dependency injection, configuration, and security support.
- Easy integration with JDBC and REST APIs.

### 3.2 LLM Orchestration
- LangChain4j

Why:
- Simplifies prompt orchestration and tool calling.
- Supports clean abstractions for future MCP migration.

### 3.3 LLM Provider
- Primary: OpenAI GPT models
- Optional fallback: Gemini or Claude

Why:
- High quality text-to-SQL generation.
- Provider abstraction allows switching by environment and cost profile.

### 3.4 Relational Database
- Production: PostgreSQL 16+
- Local demo/testing: H2

Why:
- PostgreSQL is robust, SQL-compliant, and enterprise-proven.
- H2 reduces setup friction for local development and CI smoke tests.

### 3.5 Data Access Layer
- JDBC (Spring JDBC)
- Connection pooling: HikariCP (default in Spring Boot)

Why:
- Direct, controlled SQL execution with predictable performance.
- Simple to enforce guardrails before query execution.

### 3.6 Retrieval and Embeddings (RAG)
- Embedding model: provider-managed embedding API
- Vector storage: pgvector extension on PostgreSQL

Why:
- Keeps operations simple by using the same DB platform.
- Reduces token usage by retrieving only relevant schema/rule context.

### 3.7 API and Integration
- REST API for web client integration.
- MCP protocol in phase 2 for standardized tool interoperability.

Why:
- REST is easy for immediate product integration.
- MCP enables plug-and-play integration across compatible clients.

### 3.8 Frontend
- Server-side rendering via Spring template engine (Thymeleaf) for MVP
- Optional upgrade path: React + TypeScript

Why:
- Fast MVP delivery with minimal frontend complexity.
- Clear path to richer UI when product matures.

### 3.9 Testing
- Unit/Integration: JUnit 5 + Mockito + Spring Boot Test
- API tests: REST Assured
- Security/adversarial tests: custom prompt and SQL guardrail suites

Why:
- Standard Java testing stack.
- Good coverage for functional, integration, and safety behavior.

### 3.10 Observability and Operations
- Logging: SLF4J + Logback (JSON format in non-local env)
- Metrics/Monitoring: Micrometer + Prometheus + Grafana
- Tracing: OpenTelemetry (optional in phase 1, recommended in phase 2)

Why:
- Enables auditability and production diagnostics.
- Supports latency and guardrail KPI tracking.

### 3.11 Build and Delivery
- Build tool: Maven
- Containerization: Docker
- CI/CD: GitHub Actions

Why:
- Widely adopted and easy to standardize across teams.

## 4. Security Technology Decisions
- SQL validation layer: custom guardrail service using parser/regex hybrid.
- Secrets management: environment variables or secret manager (no hardcoded credentials).
- Transport security: HTTPS for external traffic.
- Access control: API authentication token/JWT for enterprise deployment.

## 5. Version Baseline (Initial)
- Java: 21 LTS
- Spring Boot: 4.0+
- PostgreSQL: 16+
- Maven: 3.9+

## 6. Environments
- Local: H2 + mock/low-cost LLM model.
- Dev/UAT: PostgreSQL + target LLM provider.
- Production: PostgreSQL HA configuration + full monitoring stack.

## 7. Non-Selected Alternatives (For Record)
- Quarkus/Micronaut instead of Spring Boot: not selected due to team familiarity and timeline.
- External vector DB (Qdrant/Pinecone): deferred to later scale stage.
- GraphQL API: deferred to reduce complexity in early releases.

## 8. Review and Change Policy
- Any technology change must include:
  - Reason for change
  - Impact on cost, security, performance, and delivery timeline
  - Migration and rollback plan

Owner: Solution Architect
Review frequency: every sprint or when major risks/issues appear
