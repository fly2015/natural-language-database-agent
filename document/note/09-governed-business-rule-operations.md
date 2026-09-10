# Governed Business Rule Operations

## Purpose
Operational notes for using governed business-rule storage while keeping YAML mode available.

## Source Modes
YAML remains the default local/test mode:

```yaml
agent:
  retrieval:
    business-rule-source: yaml
```

Database-backed rules can be enabled with:

```yaml
agent:
  retrieval:
    business-rule-source: database
    datasource-id: default
    tenant-id: ""
```

Environment variable equivalent:

```powershell
$env:AGENT_RETRIEVAL_BUSINESS_RULE_SOURCE="database"
$env:AGENT_DATASOURCE_ID="default"
$env:AGENT_TENANT_ID=""
```

## PostgreSQL Database Separation
PostgreSQL mode uses separate logical databases by concern:

```text
nlda_app          datasource data queried by the NL-to-SQL flow
nlda_governance   governed business-rule source of truth and rebuild audit log
nlda_retrieval    pgvector and pg_trgm retrieval indexes
```

The app datasource inspects and queries only `nlda_app`.
Governed rule CRUD and approved-rule reads use `nlda_governance`.
Semantic retrieval and vocabulary correction use `nlda_retrieval`.

Flyway migration ownership:

```text
src/main/resources/db/datasource/migration   nlda_app
src/main/resources/db/governance/migration   nlda_governance
src/main/resources/db/retrieval/migration    nlda_retrieval
```

Spring SQL init files are not used in PostgreSQL mode. Keep schema and seed changes in the appropriate Flyway folder.

Default local connection variables:

```powershell
$env:POSTGRES_APP_JDBC_URL="jdbc:postgresql://localhost:5432/nlda_app"
$env:POSTGRES_GOVERNANCE_JDBC_URL="jdbc:postgresql://localhost:5432/nlda_governance"
$env:POSTGRES_RETRIEVAL_JDBC_URL="jdbc:postgresql://localhost:5432/nlda_retrieval"
$env:POSTGRES_USER="nlda"
$env:POSTGRES_PASSWORD="nlda"
```

## Local Test Mode
Run default in-memory tests:

```powershell
.\mvnw25.cmd test
```

The local profile uses the in-memory datasource and in-memory vector repository by default.

## PostgreSQL Docker Mode
By default Docker uses the deterministic local LLM. To use OpenAI, set these environment variables before starting Compose:

```powershell
$env:AGENT_LLM_PROVIDER="openai"
$env:OPENAI_API_KEY="your-api-key"
$env:OPENAI_MODEL="gpt-4.1-mini"
```

Start the full PostgreSQL stack:

```powershell
docker compose up -d --build
```

If the local Docker volume existed before database separation, create the new databases once:

```powershell
docker exec nlda-postgres-database-server createdb -U nlda nlda_app
docker exec nlda-postgres-database-server createdb -U nlda nlda_governance
docker exec nlda-postgres-database-server createdb -U nlda nlda_retrieval
```

Ignore `already exists` errors when rerunning those commands.

Check the containers:

```powershell
docker compose ps
```

Docker Compose runs one local PostgreSQL server as `postgres-database-server`.
The app connects through purpose-specific network aliases:

```text
datasource-postgres   -> nlda_app
governance-postgres   -> nlda_governance
retrieval-postgres    -> nlda_retrieval
```

The app service is `app-full-postgres` with:

```yaml
SPRING_PROFILES_ACTIVE: full-postgres
```

The app remains available at:

```text
http://localhost:8080/
http://localhost:8080/admin
```

PostgreSQL mode uses:
- `nlda_governance` governed rule tables as source of truth
- `nlda_retrieval` `pgvector` as the semantic retrieval index
- `nlda_retrieval` `pg_trgm` for typo/fuzzy vocabulary lookup
- `nlda_app` datasource tables for schema inspection and SQL execution

## Embedding Index Operations
PostgreSQL mode defaults to OpenAI embeddings:

```yaml
AGENT_RETRIEVAL_EMBEDDING_PROVIDER: openai
AGENT_RETRIEVAL_EMBEDDING_MODEL: text-embedding-3-small
```

Local/default mode uses fake deterministic embeddings:

```yaml
AGENT_RETRIEVAL_EMBEDDING_PROVIDER: fake
AGENT_RETRIEVAL_EMBEDDING_MODEL: fake-hash-embedding
```

OpenAI embeddings require `OPENAI_API_KEY`. Embedding calls are separate from chat/SQL LLM calls. They are billed as embedding input tokens, not chat completion tokens. The configured OpenAI project must have access to the selected embedding model.

Schema and governed business-rule embeddings are built during explicit index rebuilds or refresh operations, then stored in `nlda_retrieval.retrieval_chunk_embedding` for later pgvector search. Normal user queries do not rebuild all embeddings. When semantic retrieval is active, each user query may call the embedding provider once to create the query vector.

Manual rebuild:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/admin/retrieval-index/rebuild
```

Schema refresh and rebuild:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/admin/schema/refresh
```

Business-rule rebuild:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/admin/retrieval-index/business-rules/rebuild
```

Selected rule rebuild:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/admin/business-rules/rule.revenue/reindex
```

Inspect stored embedding models:

```powershell
docker exec nlda-postgres-database-server psql -U nlda -d nlda_retrieval -c "select embedding_model, active, count(*) from retrieval_chunk_embedding group by embedding_model, active order by embedding_model, active;"
```

If rebuild fails with `model_not_found`, change `AGENT_RETRIEVAL_EMBEDDING_MODEL` to an embedding model available to the OpenAI project, recreate the app container, and rerun the rebuild. Failed embedding rebuilds do not write partial vectors to pgvector.

## Admin UI
Open:

```text
http://localhost:8080/admin
```

Admin sections:
- Business Rules
- Schema Index / Refresh
- Retrieval Index / Rebuild
- Retrieval Diagnostics

## Main Flow Boundary
The main NL-to-SQL query flow is consume-only.

It reads ready schema chunks, approved business-rule chunks, vocabulary, and vector records. It does not approve rules, refresh schema, rebuild chunks, generate embeddings, or mutate vector indexes inline.

Refresh and rebuild operations should be done by:
- admin UI/API
- startup preparation
- scheduled/background jobs later
- explicit operator-triggered APIs
