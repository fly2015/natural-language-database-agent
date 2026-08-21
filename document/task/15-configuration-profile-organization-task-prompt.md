# Task Prompt: Configuration Profile Organization

## Source
- Checklist: `document/note/07-enterprise-readiness-checklist.md`
- Related sections:
  - `12. Infrastructure`
  - `13. Admin Management`
  - `14. Enterprise Target Summary`
- Related implemented task: `document/task/14-business-rules-governed-storage-task-prompt.md`

## Objective
Reorganize application configuration into clear, maintainable Spring profiles and purpose-specific config files.

The system should support three startup shapes:

```text
1. Full local mode
2. Full PostgreSQL mode
3. Custom mixed-provider mode
```

The goal is to make configuration easier to understand, test, and operate by separating the three database ownership concerns: datasource, governance, and retrieval.

The configuration and container setup should also make future datasource providers easy to add. PostgreSQL is the current full database provider, but the structure should leave a clear path for later MySQL, SQL Server, Oracle, or other enterprise databases.

## Revision Note
This task remains the correct place for application YAML/profile cleanup.

The database ownership task now separates:

```text
nlda_app          datasource database queried by the NL-to-SQL flow
nlda_governance   governed business-rule database
nlda_retrieval    retrieval/vector database
```

This configuration task should now focus on making that separation obvious in YAML and profile names.

## Implementation Prompt
You are working in the `natural-language-database-agent` repository. Refactor configuration files so the application can be started predictably in local memory/file mode or full database mode without mixing unrelated settings in one large file.

Current state:
- `application.yml` is smaller, but still contains the root `agent` block for SQL and LLM defaults.
- `application-full-local.yml` imports the complete local stack.
- `application-full-postgres.yml` imports PostgreSQL config for datasource, governance, and retrieval.
- `application-custom.yml` imports optional custom config slots for mixed providers.
- `config/datasource-*.yml`, `config/governance-*.yml`, and `config/retrieval-*.yml` own purpose-specific settings.
- Docker Compose activates the database/PostgreSQL stack and provides three PostgreSQL JDBC URLs.
- Flyway now owns application, governance, and retrieval database migrations.

Required outcome:
- Clear profile names for local memory/file mode and governed enterprise database mode.
- Purpose-specific config files that are easy to scan and maintain.
- Profile-specific datasource, governance, and retrieval settings are moved out of `application.yml` and into dedicated imported config files.
- No accidental mixing of local YAML/in-memory behavior with production-like PostgreSQL/database behavior.
- Provider-specific datasource settings are isolated so new database providers can be added without rewriting common retrieval or governance configuration.
- Existing tests and Docker Compose still work.

## Recommended Profile Model

Use profile names that describe runnable compositions:

```text
full-local
full-postgres
custom
```

Recommended meaning:
- `full-local`: H2 app datasource, YAML business rules, in-memory vocabulary, in-memory vector store, fake embeddings, deterministic LLM.
- `full-postgres`: PostgreSQL for app datasource, governance, and retrieval, including `pg_trgm` and `pgvector`.
- `custom`: optional import slots for mixing datasource, governance, retrieval, and LLM implementations.

The full PostgreSQL stack is activated with one composition profile:

```text
SPRING_PROFILES_ACTIVE=full-postgres
```

Legacy `database,postgres`, `local-memory`, `governed-database`, and `postgres-provider` profile names are replaced by composition profiles in runtime config and documentation.

## Functional Requirements

1. Base configuration
   - Keep `application.yml` small.
   - Put only common application settings in the base file.
   - Keep common SQL and LLM defaults in `application.yml` unless they become provider-specific.
   - Avoid putting profile-specific datasource, rule-source, vector, vocabulary, LLM provider, or SQL init settings in the base file unless they are truly common.
   - `application.yml` should mainly contain application name, default profile, server/management basics, and config imports.

2. Full local profile
   - Add or reorganize a `full-local` profile for fast local development and tests.
   - Local mode should use:
     - H2 datasource
     - YAML business rules
     - in-memory vocabulary
     - in-memory vector repository
     - fake deterministic embeddings
     - deterministic LLM
   - It should not require Docker or PostgreSQL.
   - It should remain suitable for `.\mvnw25.cmd test`.

3. Full PostgreSQL profile
   - Add or reorganize a `full-postgres` profile for the complete PostgreSQL stack.
   - Full PostgreSQL mode should use:
     - database-backed business rules
     - Flyway migrations
     - consume-only main query flow
     - admin/governance APIs for refresh/rebuild
     - PostgreSQL JDBC URL/user/password for `nlda_app`, `nlda_governance`, and `nlda_retrieval`
     - `pg_trgm` vocabulary provider
     - `pgvector` vector provider
     - PostgreSQL Flyway target datasource settings
     - datasource id and tenant id environment variables
   - Full PostgreSQL mode should not use YAML business rules at runtime unless explicitly configured as seed/bootstrap behavior.
   - Docker Compose activation is covered by the separate Docker/Compose maintainability task.

4. Custom mixed-provider profile
   - Add or reorganize a `custom` profile for explicitly mixed provider configurations.
   - Custom mode should import optional files such as:
     - `config/datasource-custom.yml`
     - `config/governance-custom.yml`
     - `config/retrieval-custom.yml`
   - Custom mode should make it possible to run combinations such as PostgreSQL app datasource with another governance or retrieval provider without changing full-local or full-postgres.

5. Datasource provider extensibility
   - Keep provider-neutral settings separate from provider-specific settings.
   - Provider-neutral settings include:
     - business-rule source mode
     - consume-only main flow
     - admin/governance enablement
     - embedding provider
     - common LLM settings
     - common retrieval thresholds
   - Provider-specific settings include:
     - datasource JDBC URL/user/password
     - SQL dialect
     - metadata provider
     - vocabulary provider
     - vector provider
     - SQL init or migration differences
     - provider-specific validation behavior
   - Adding a new datasource provider should generally mean adding a new profile/config file and adapter beans, not editing unrelated local or PostgreSQL config.
   - Example future files:

```text
application-mysql.yml
application-sqlserver.yml
application-oracle.yml
```

   - Do not hardcode PostgreSQL assumptions into common database-mode configuration.

6. Purpose-specific config files
   - Split configuration by purpose where it improves readability.
   - Suggested files:

```text
application.yml
application-full-local.yml
application-full-postgres.yml
application-custom.yml
business-rules.yml
```

   - Keep agent credentials and common SQL/LLM defaults in a dedicated agent config file. Keep database ownership config files named by purpose. Suggested structure:

```text
config/agent-common.yml
config/datasource-memory.yml
config/datasource-postgres.yml
config/datasource-mysql.yml later
config/datasource-sqlserver.yml later
config/datasource-oracle.yml later
config/governance-yaml.yml
config/governance-postgres.yml
config/governance-mysql.yml later
config/governance-sqlserver.yml later
config/governance-oracle.yml later
config/retrieval-memory.yml
config/retrieval-postgres.yml
config/retrieval-mysql.yml later
config/retrieval-sqlserver.yml later
config/retrieval-oracle.yml later
```

   - Do not over-split if the result becomes harder to navigate.
   - Keep database ownership files limited to the three purpose prefixes: `datasource-`, `governance-`, and `retrieval-`.

7. Environment variable consistency
   - Use a consistent environment variable naming pattern.
   - Keep existing environment variables working when practical.
   - Document important variables:
     - `SPRING_PROFILES_ACTIVE`
     - `POSTGRES_APP_JDBC_URL`
     - `POSTGRES_GOVERNANCE_JDBC_URL`
     - `POSTGRES_RETRIEVAL_JDBC_URL`
     - `POSTGRES_USER`
     - `POSTGRES_PASSWORD`
     - `AGENT_RETRIEVAL_BUSINESS_RULE_SOURCE`
     - `AGENT_RETRIEVAL_VOCABULARY_PROVIDER`
     - `AGENT_RETRIEVAL_VECTOR_PROVIDER`
     - `AGENT_RETRIEVAL_EMBEDDING_PROVIDER`
     - `AGENT_DATASOURCE_ID`
     - `AGENT_TENANT_ID`
     - `AGENT_LLM_PROVIDER`
     - future provider-specific variables such as `MYSQL_JDBC_URL`, `SQLSERVER_JDBC_URL`, or `ORACLE_JDBC_URL`

8. Tests
   - Existing test suite should keep passing.
   - Tests should clearly run in local memory/file mode unless explicitly testing database mode.
   - Add or update tests that prove:
     - `full-local` uses YAML/in-memory behavior
     - `full-postgres` uses database rules and PostgreSQL providers
     - `custom` can import purpose-specific mixed provider files without changing full-local or full-postgres
     - legacy `database,postgres`, `local-memory`, `governed-database`, and `postgres-provider` profile names are intentionally removed with documentation updates

9. Documentation
   - Update or add documentation that explains how to run local/profile verification:

```powershell
.\mvnw25.cmd test
```

   - Document profile intent and which config file owns which concern.
   - Include IntelliJ/DataGrip connection details for PostgreSQL if useful.
   - Document how to add a new datasource provider following the established profile pattern.
   - Dockerfile and Docker Compose organization is covered by the separate Docker/Compose maintainability task.

## Non-Goals
- Do not rewrite business-rule governance logic.
- Do not replace Docker Compose with Kubernetes or production deployment tooling.
- Do not introduce secret management beyond clean local environment variable boundaries.
- Do not remove YAML support.
- Do not change the main query behavior except where required to respect profile selection.
- Do not implement MySQL, SQL Server, Oracle, or other providers in this task unless explicitly requested.

## Acceptance Criteria
- `application.yml` is reduced to common settings and profile activation/import structure.
- `application.yml` contains only common app, SQL, and LLM defaults.
- Purpose-specific settings live under `src/main/resources/config` with only `datasource-*`, `governance-*`, and `retrieval-*` file names.
- Local mode can run without PostgreSQL and uses YAML/in-memory providers.
- Governed database mode can run with PostgreSQL and uses database-backed rules, `pg_trgm`, and `pgvector`.
- Custom mixed-provider settings are purpose-specific and do not require changing the full-local or full-postgres profiles.
- Profile names are more informative than generic `database`/`postgres`, with compatibility strategy documented.
- Documentation explains the profile pattern for future providers.
- `.\mvnw25.cmd test` passes.
- Admin UI remains available at `/admin`.
- Query UI remains available at `/`.
- Documentation explains which profile to use for which scenario.

## Suggested Files To Inspect First
- `src/main/resources/application.yml`
- `src/main/resources/application-full-postgres.yml`
- `src/main/resources/business-rules.yml`
- `document/note/09-governed-business-rule-operations.md`
- `src/test/java`

## Validation Commands

Run local tests:

```powershell
.\mvnw25.cmd test
```

Verify health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Verify governed rules:

```powershell
Invoke-RestMethod http://localhost:8080/api/admin/business-rules
```
