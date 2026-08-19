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

## H2 Test Mode
Run default H2 tests:

```powershell
.\mvnw.cmd test
```

H2 uses the in-memory vector repository by default.

## PostgreSQL Docker Mode
Start PostgreSQL:

```powershell
docker compose up -d postgres
```

Run the app against PostgreSQL:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=postgres
```

PostgreSQL mode uses:
- governed rule tables as source of truth
- `pgvector` as the semantic retrieval index
- `pg_trgm` for typo/fuzzy vocabulary lookup

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
