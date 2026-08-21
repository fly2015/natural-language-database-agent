# Task Prompt: Docker And Compose Provider Organization

## Source
- Related config task: `document/task/15-configuration-profile-organization-task-prompt.md`
- Related implemented task: `document/task/14-business-rules-governed-storage-task-prompt.md`
- Current files:
  - `Dockerfile`
  - `docker-compose.yml`

## Objective
Reorganize Dockerfile and Docker Compose setup so the current PostgreSQL stack is easy to run and future datasource providers can be added without turning Compose into a hard-to-maintain file.

PostgreSQL is the current full database provider, but the container structure should leave a clear path for future MySQL, SQL Server, Oracle, or other enterprise datasource providers.

## Implementation Prompt
You are working in the `natural-language-database-agent` repository. Refactor Docker and Compose configuration for maintainability, provider extensibility, and clear local operation.

The Spring configuration/profile organization is handled in a separate task. This task should focus only on container build/runtime structure.

## Functional Requirements

1. Dockerfile maintainability
   - Keep app build concerns in `Dockerfile`.
   - Keep runtime service wiring in Compose.
   - Do not hardcode datasource-provider-specific environment variables in the Dockerfile.
   - Keep the Dockerfile usable for all datasource providers.
   - Use the existing Maven wrapper and Java 25 base image unless there is a clear reason to change.

2. Compose structure
   - Make the active runtime mode obvious.
   - Keep PostgreSQL credentials in Compose only for local development.
   - Do not hardcode production secrets.
   - Avoid duplicating large environment blocks when adding more datasource services.
   - Group common app environment variables separately from provider-specific variables where Compose supports it through anchors or override files.

3. Provider expansion pattern
   - Define a repeatable pattern for adding a new datasource provider:

```text
1. Add application-<provider>.yml in the Spring config task
2. Add provider-specific metadata/retrieval adapter beans
3. Add provider service to Compose or provider override file
4. Add app service/profile wiring for that provider
5. Add smoke test command and documentation
```

   - PostgreSQL should be the reference implementation for this pattern.

4. Compose profiles or override files
   - Choose a maintainable strategy for optional provider stacks.
   - Preferred options:

```text
Option A: Compose profiles
docker compose --profile postgres up -d
docker compose --profile mysql up -d later
```

```text
Option B: Provider override files
docker compose -f docker-compose.yml -f docker-compose.postgres.yml up -d
docker compose -f docker-compose.yml -f docker-compose.mysql.yml up -d later
```

   - Use whichever approach keeps the repository easiest to understand.
   - Document the chosen strategy.

5. Service naming
   - Use clear service names that can scale to more providers.
   - Examples:

```text
postgres
app-postgres
mysql later
app-mysql later
```

   - If keeping a single `app` service, make provider-specific profiles/env explicit.

6. PostgreSQL local stack
   - Preserve current PostgreSQL capabilities:
     - `pgvector/pgvector:pg17`
     - database `nlda`
     - local credentials `nlda` / `nlda`
     - port `5432`
     - app port `8080`
     - active Spring profiles for full database PostgreSQL mode
     - database-backed business rules
     - `postgres-trgm`
     - `pgvector`
   - Do not break the current app and admin URLs:

```text
http://localhost:8080/
http://localhost:8080/admin
```

7. Documentation
   - Document the local PostgreSQL credentials.
   - Document how to start/stop the stack.
   - Document how to inspect tables.
   - Document how future datasource providers should be added to Docker/Compose.

## Non-Goals
- Do not implement MySQL, SQL Server, Oracle, or other providers in this task unless explicitly requested.
- Do not replace Docker Compose with Kubernetes.
- Do not introduce production secret management.
- Do not rewrite Spring profiles; that belongs to the configuration profile task.

## Acceptance Criteria
- Docker Compose starts the PostgreSQL full database stack clearly.
- Dockerfile remains provider-neutral.
- Compose organization has an obvious pattern for adding more datasource providers.
- Local PostgreSQL credentials and connection details are documented.
- `docker compose up -d --build` or the chosen equivalent starts the app and PostgreSQL.
- App health endpoint returns `UP`.
- Admin UI remains available at `/admin`.
- Query UI remains available at `/`.
- A representative query succeeds against the PostgreSQL-backed stack.

## Suggested Files To Inspect First
- `Dockerfile`
- `docker-compose.yml`
- `src/main/resources/application-postgres-provider.yml`
- `document/note/09-governed-business-rule-operations.md`
- `document/task/15-configuration-profile-organization-task-prompt.md`

## Validation Commands

Start the stack:

```powershell
docker compose up -d --build
```

Check services:

```powershell
docker compose ps
```

Verify health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Verify governed rules:

```powershell
Invoke-RestMethod http://localhost:8080/api/admin/business-rules
```

Inspect PostgreSQL tables:

```powershell
docker exec nlda-postgres psql -U nlda -d nlda -c "select schemaname, tablename from pg_tables where schemaname = 'public' order by tablename;"
```
