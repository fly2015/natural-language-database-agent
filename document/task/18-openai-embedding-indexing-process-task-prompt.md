# Task Prompt: OpenAI Embedding Indexing Process

## Source
- Related config task: `document/task/15-configuration-profile-organization-task-prompt.md`
- Related compose task: `document/task/16-docker-compose-provider-organization-task-prompt.md`
- Related database task: `document/task/17-database-organization-task-prompt.md`
- Current embedding/vector files:
  - `src/main/java/com/nlda/retrieval/impl/embedding/LangChain4jOpenAiEmbeddingClient.java`
  - `src/main/java/com/nlda/retrieval/impl/embedding/FakeEmbeddingClient.java`
  - `src/main/java/com/nlda/retrieval/index/EmbeddingIndexService.java`
  - `src/main/java/com/nlda/retrieval/index/SchemaIndexService.java`
  - `src/main/java/com/nlda/retrieval/governance/RetrievalIndexRebuildService.java`
  - `src/main/java/com/nlda/retrieval/impl/vector/PgVectorRetrievalRepository.java`
  - `src/main/resources/config/retrieval-postgres.yml`
  - `src/main/resources/config/retrieval-memory.yml`
  - `src/main/resources/db/retrieval/migration`

## Objective
Implement a clear OpenAI embedding indexing process for schema and governed business rules, with explicit admin rebuild controls and automatic rebuild when indexed content changes.

The system should use OpenAI embeddings in the PostgreSQL profile and fake deterministic embeddings in the local profile. OpenAI embeddings should be generated once for indexed schema/rule chunks and stored in pgvector for later semantic search. User queries may still call the embedding provider once at request time to create the query vector for pgvector search.

## Required Outcome
- PostgreSQL profile defaults to `agent.retrieval.embedding.provider=openai`.
- Local memory profile defaults to `agent.retrieval.embedding.provider=fake`.
- Schema chunks and governed business-rule chunks are embedded and stored in `nlda_retrieval.retrieval_chunk_embedding`.
- Rebuilds update the stored `embedding_model` when the configured embedding model changes.
- Rebuilds do not silently report success when OpenAI embedding calls fail.
- Admin APIs can rebuild:
  - the full embedding index
  - schema embeddings
  - business-rule embeddings
  - one selected business-rule embedding set
- Automatic rebuild happens when schema/rule fingerprint or embedding model changes.
- Query-time semantic search uses stored pgvector embeddings plus one embedding call for the user query.

## Implementation Prompt
You are working in the `natural-language-database-agent` repository. Refactor and implement the embedding indexing process so the system has a robust build-time indexing flow and a clear query-time semantic retrieval flow.

Use the existing Spring Boot, LangChain4j, Flyway, and pgvector patterns already present in the codebase. Keep local/dev behavior deterministic and cheap with `fake` embeddings. Use OpenAI embeddings only when the PostgreSQL profile or explicit configuration selects `openai`.

## Conceptual Flow

Index/build time:

```text
schema metadata + governed business rules
-> chunk builder
-> canonical chunk text
-> embedding provider
-> vector values
-> pgvector table
```

Query time:

```text
user question
-> query embedding provider
-> pgvector similarity search
-> retrieved schema/rule chunks
-> LLM SQL generation
-> guarded SQL execution
```

## Functional Requirements

1. Provider configuration
   - Keep provider selection under:

```yaml
agent.retrieval.embedding.provider
```

   - Supported providers:

```text
fake
openai
none, only if explicitly configured for disabling semantic embeddings
```

   - PostgreSQL profile default:

```yaml
provider: ${AGENT_RETRIEVAL_EMBEDDING_PROVIDER:openai}
model: ${AGENT_RETRIEVAL_EMBEDDING_MODEL:text-embedding-3-small}
```

   - Local memory profile default:

```yaml
provider: ${AGENT_RETRIEVAL_EMBEDDING_PROVIDER:fake}
model: ${AGENT_RETRIEVAL_EMBEDDING_MODEL:fake-hash-embedding}
```

2. Embedding client responsibilities
   - `LangChain4jOpenAiEmbeddingClient` should call only the OpenAI embedding model API.
   - It must not call the chat/LLM SQL-generation client.
   - `FakeEmbeddingClient` remains deterministic for local tests and smoke checks.
   - If `provider=none`, no `EmbeddingClient` should be created.

3. Embedding index service
   - `EmbeddingIndexService` should:
     - canonicalize each chunk
     - call `EmbeddingClient.embed(...)`
     - collect `VectorIndexedChunk` records
     - write vectors only after all required chunk embeddings are successfully created
   - Do not update pgvector if any chunk embedding fails.
   - Surface failures as failed rebuilds, not successful rebuilds with warnings only.
   - Include useful failure metadata:
     - chunk id
     - embedding model
     - failed count
     - first failure message

4. pgvector storage behavior
   - Store current embeddings in `retrieval_chunk_embedding`.
   - Enforce one current row per:

```text
datasource_id + schema_fingerprint + chunk_id
```

   - Rebuild with a different `embedding_model` should update the existing row's `embedding_model`, dimensions, vector, content hash, and `updated_at`.
   - Do not create parallel active rows for old embedding models.
   - Old/stale schema fingerprints may be marked inactive.
   - Provide a Flyway migration for existing databases if the current uniqueness constraint includes `embedding_model`.

5. Rebuild triggers
   - Manual admin rebuild:

```http
POST /api/admin/retrieval-index/rebuild
```

   - Schema refresh:

```http
POST /api/admin/schema/refresh
```

   - Business-rule rebuild:

```http
POST /api/admin/retrieval-index/business-rules/rebuild
```

   - Selected rule rebuild:

```http
POST /api/admin/business-rules/{id}/reindex
```

   - Business-rule approval and deactivation should trigger rebuild of affected retrieval chunks.
   - Saving a draft should not rebuild production embeddings until approval or explicit reindex.

6. Automatic rebuild when fingerprint changes
   - Compute a stable schema fingerprint from datasource metadata.
   - Compute stable content hashes for schema chunks and business-rule chunks.
   - Compute an embedding index key:

```text
schemaFingerprint + embeddingModel
```

   - If the current schema fingerprint differs from the stored/current fingerprint, rebuild support indexes.
   - If the embedding model differs from the stored/current model, rebuild embeddings.
   - Avoid rebuilding on every query when fingerprints and model are unchanged.
   - Query retrieval should prefer ready indexes and only trigger indexing through explicit refresh/ensure-index paths.

7. Admin visibility
   - Admin diagnostics should show:
     - embedding provider
     - embedding model
     - indexed record count
     - active indexed record count
     - last rebuild outcome
     - last rebuild error, if any
   - `GET /api/admin/retrieval-index` should make it obvious whether rows are active or stale.
   - Rebuild responses should distinguish:

```text
OK
FAILED
PARTIAL, only if partial mode is intentionally supported
```

   - Prefer failing the rebuild over partial success unless there is a documented partial-index behavior.

8. Query-time semantic retrieval
   - `DynamicSchemaRetriever` should call `EmbeddingClient.embed(queryText)` once per query only when:
     - an `EmbeddingClient` is available
     - a `VectorRetrievalRepository` is available
     - the ready index has active embeddings for the current fingerprint/model
   - If semantic retrieval fails, lexical retrieval may continue, but logs/diagnostics should clearly indicate semantic failure.
   - Query-time embedding failures should not corrupt stored embeddings.

9. OpenAI operational behavior
   - Document that OpenAI embedding calls require `OPENAI_API_KEY`.
   - Document that embedding calls are billed as embedding input tokens, not chat/LLM tokens.
   - Document that the configured OpenAI project must have access to the selected embedding model.
   - Do not log the API key.
   - Model access errors such as `model_not_found` should be visible in rebuild failure responses/logs.

10. Tests
   - Profile configuration tests:
     - local/default profile uses `fake`
     - full PostgreSQL profile uses `openai`
   - Repository/upsert tests:
     - rebuilding the same chunk with a new embedding model updates `embedding_model`
     - rebuilding does not create duplicate active rows per chunk
   - Service tests:
     - embedding rebuild writes vectors only when all chunks embed successfully
     - embedding rebuild fails when the provider fails for one or more chunks
     - schema fingerprint change triggers rebuild
     - embedding model change triggers rebuild
   - Admin/API tests where practical:
     - manual rebuild returns `OK` when embeddings succeed
     - manual rebuild returns failure when OpenAI/provider embedding fails

## Suggested Implementation Steps

1. Confirm profile defaults
   - Check `retrieval-postgres.yml`.
   - Check `retrieval-memory.yml`.
   - Check `docker-compose.yml`.
   - Update `ProfileConfigurationTest`.

2. Harden embedding rebuild behavior
   - Update `EmbeddingIndexService` so it does not call `vectorRepository.replace(...)` if any embedding call fails.
   - Return or throw an explicit failure with useful details.

3. Update pgvector uniqueness and upsert
   - Add a Flyway migration under `db/retrieval/migration`.
   - Change unique constraint from:

```text
datasource_id + schema_fingerprint + chunk_id + embedding_model
```

     to:

```text
datasource_id + schema_fingerprint + chunk_id
```

   - Update `PgVectorRetrievalRepository.replace(...)` to set `embedding_model = EXCLUDED.embedding_model` on conflict.

4. Split rebuild semantics if needed
   - If the current rebuild service always rebuilds all chunks, keep that first.
   - If performance becomes a concern, introduce targeted rebuilds by chunk id/rule id.
   - Ensure selected rule rebuild does not leave stale old rule vectors active.

5. Improve admin diagnostics
   - Extend diagnostics DTOs as needed.
   - Include provider/model and last rebuild result.
   - Keep response payloads small and operationally useful.

6. Validate query-time semantic retrieval
   - Confirm query embedding uses the same model as indexed embeddings.
   - Confirm pgvector search filters by current fingerprint and current model.
   - Confirm lexical retrieval still works when semantic retrieval is unavailable.

7. Document operation
   - Add notes for local fake embeddings.
   - Add notes for OpenAI embeddings.
   - Add rebuild commands.
   - Add troubleshooting for missing key/model access.

## Non-Goals
- Do not implement a new embedding provider unless explicitly requested.
- Do not introduce a separate worker/microservice unless current in-process rebuilds are proven too slow.
- Do not replace pgvector.
- Do not call the chat/SQL LLM for embedding generation.
- Do not rebuild embeddings on every user request.

## Acceptance Criteria
- `full-postgres` profile defaults to OpenAI embeddings.
- local/default profile defaults to fake embeddings.
- Manual rebuild with a working OpenAI embedding model stores active pgvector rows with `embedding_model=text-embedding-3-small` or the configured model.
- Rebuilding with a different model updates `embedding_model` in existing chunk rows instead of creating duplicate active rows.
- Rebuild fails clearly if OpenAI rejects the model or API key.
- No pgvector writes occur when embedding generation fails.
- Schema fingerprint change triggers support-index rebuild.
- Embedding model change triggers embedding rebuild.
- Business-rule approval/deactivation/reindex triggers affected retrieval rebuild.
- Normal user query does not rebuild all schema/rule embeddings.
- Normal user query may call the embedding API once for the query vector when semantic retrieval is enabled.
- App health remains `UP` after startup.
- Representative query succeeds after embeddings are indexed.

## Validation Commands

Start PostgreSQL stack:

```powershell
docker compose up -d --build
```

Verify provider in container:

```powershell
docker exec nlda-app-full-postgres printenv | Select-String -Pattern 'AGENT_RETRIEVAL_EMBEDDING_PROVIDER|AGENT_RETRIEVAL_EMBEDDING_MODEL|OPENAI_API_KEY'
```

Verify health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Trigger full rebuild:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/admin/retrieval-index/rebuild
```

Inspect embedding rows:

```powershell
docker exec nlda-postgres-database-server psql -U nlda -d nlda_retrieval -c "select embedding_model, active, count(*) from retrieval_chunk_embedding group by embedding_model, active order by embedding_model, active;"
```

Verify one current row per chunk key:

```powershell
docker exec nlda-postgres-database-server psql -U nlda -d nlda_retrieval -c "select count(*) as rows, count(distinct (datasource_id, schema_fingerprint, chunk_id)) as distinct_chunk_keys from retrieval_chunk_embedding;"
```

Run focused profile tests:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace eclipse-temurin:25-jdk ./mvnw -q -Dtest=ProfileConfigurationTest test
```

Run full test suite:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace eclipse-temurin:25-jdk ./mvnw test
```

## Troubleshooting
- If rebuild fails with `OPENAI_API_KEY` missing, pass the key into the app environment and recreate the container.
- If rebuild fails with `model_not_found`, the OpenAI project does not have access to the configured embedding model. Change `AGENT_RETRIEVAL_EMBEDDING_MODEL` to a model the project can access.
- If stored rows still show an old model, confirm the latest rebuild actually succeeded. Restarting the app does not rewrite stored embeddings.
- If `provider=none`, no embeddings are generated and semantic vector search is disabled.
