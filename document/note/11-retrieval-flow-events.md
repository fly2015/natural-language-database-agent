# Retrieval Flow Events

This note documents the request-path retrieval steps and the purpose of each `flowEvent`.

## How events work

All retrieval events are normal application logs, not audit payloads. They are correlated by the same `traceId`.

Read the events from outer boundary to inner detail:

1. `retrieval.started` means the request entered the retrieval stage.
2. `retrieval.query_processed` means the question was normalized/tokenized for one retrieval mode.
3. `retrieval.correction` means vocabulary correction was checked for that processed query.
4. `retrieval.lexical_search` means schema chunks were scored without embeddings.
5. `retrieval.semantic_search status=SKIPPED` means retrieval stayed lexical-only.
6. `retrieval.embedding.started/completed` means a query embedding was requested.
7. `retrieval.vector_search.started/completed` means that embedding was searched in the vector DB.
8. `retrieval.semantic_search status=OK` means semantic retrieval succeeded.
9. `retrieval.schema_search.completed` means lexical and semantic results were merged for one attempt.
10. `retrieval.attempt_completed` means the attempt was scored and classified.
11. `retrieval.completed` means the whole retrieval stage finished.

Example successful semantic flow:

```text
flowEvent=retrieval.started traceId=abc status=STARTED
flowEvent=retrieval.query_processed traceId=abc status=OK mode=NORMALIZED tokenCount=4 ambiguous=false
flowEvent=retrieval.correction traceId=abc status=OK mode=NORMALIZED correctionCount=0 aliasCount=0 confidence=1.0
flowEvent=retrieval.lexical_search traceId=abc status=OK mode=NORMALIZED resultCount=18
flowEvent=retrieval.embedding.started traceId=abc status=STARTED model=text-embedding-3-small inputTokenCount=4
flowEvent=retrieval.embedding.completed traceId=abc status=OK latencyMs=120 model=text-embedding-3-small dimensionCount=64
flowEvent=retrieval.vector_search.started traceId=abc status=STARTED activeEmbeddingCount=18 limit=8
flowEvent=retrieval.vector_search.completed traceId=abc status=OK latencyMs=15 resultCount=8 topChunkIds=[schema.orders, schema.customers]
flowEvent=retrieval.semantic_search traceId=abc status=OK latencyMs=140 resultCount=8
flowEvent=retrieval.schema_search.completed traceId=abc status=OK lexicalCount=18 semanticCount=8 resultCount=8
flowEvent=retrieval.attempt_completed traceId=abc status=OK attemptNumber=1 mode=NORMALIZED confidence=0.83 failureCode=NONE resultCount=8
flowEvent=retrieval.completed traceId=abc status=OK latencyMs=180 mode=NORMALIZED confidence=0.83 failureCode=NONE snippetCount=8 attemptCount=1
```

Example lexical-only flow:

```text
flowEvent=retrieval.started traceId=abc status=STARTED
flowEvent=retrieval.query_processed traceId=abc status=OK mode=NORMALIZED tokenCount=4 ambiguous=false
flowEvent=retrieval.correction traceId=abc status=OK mode=NORMALIZED correctionCount=0 aliasCount=0 confidence=1.0
flowEvent=retrieval.lexical_search traceId=abc status=OK mode=NORMALIZED resultCount=18
flowEvent=retrieval.semantic_search traceId=abc status=SKIPPED reason=no active embeddings
flowEvent=retrieval.schema_search.completed traceId=abc status=OK lexicalCount=18 semanticCount=0 resultCount=5
flowEvent=retrieval.attempt_completed traceId=abc status=OK attemptNumber=1 mode=NORMALIZED confidence=0.69 failureCode=NONE resultCount=5
flowEvent=retrieval.completed traceId=abc status=OK latencyMs=40 mode=NORMALIZED confidence=0.69 failureCode=NONE snippetCount=5 attemptCount=1
```

## Boundary events

- `retrieval.started`
  - Emitted by `AgentOrchestrator` before calling `RetrievalService`.
  - Purpose: mark the beginning of the whole retrieval stage for a user request.
  - How it works: starts the retrieval timer and uses the request `traceId`; it does not perform retrieval itself.
  - There should be exactly one per request.

- `retrieval.completed`
  - Emitted by `AgentOrchestrator` after `RetrievalService` returns.
  - Purpose: summarize the whole retrieval stage with final mode, confidence, failure code, snippet count, and attempt count.
  - How it works: reads the returned `RetrievalContext` and logs the final retrieval decision after all attempts/fallbacks are done.
  - There should be exactly one per request.

## Query processing events

- `retrieval.query_processed`
  - Emitted by `RetrievalService` for each processed query mode.
  - Purpose: show tokenization/normalization outcome for `NORMALIZED`, `EXPANDED`, or `FALLBACK_CACHE`.
  - How it works: calls `RetrievalQueryProcessor`, which normalizes text, tokenizes terms, applies vocabulary correction, expands analyzed terms, and returns a `ProcessedQuery`.

- `retrieval.correction`
  - Emitted by `RetrievalService` after query processing.
  - Purpose: show whether vocabulary correction changed or expanded the query, without logging raw correction payloads.
  - How it works: reads correction metadata from `ProcessedQuery`, including corrected term count, alias count, ambiguity status, and correction confidence.
  - PostgreSQL mode: calls the `postgres-trgm` vocabulary service when `agent.retrieval.vocabulary.provider=postgres-trgm`.
  - In-memory mode: calls the in-memory vocabulary service when `agent.retrieval.vocabulary.provider=in-memory`.

- `retrieval.query_ambiguous`
  - Emitted by `DynamicSchemaRetriever` only when query correction is ambiguous.
  - Purpose: explain why confidence may be penalized or why retrieval may ask for clarification.
  - How it works: checks `ProcessedQuery.ambiguous()` before scoring chunks; when true, later scoring applies a lower correction penalty.

## Attempt events

- `retrieval.attempt_completed`
  - Emitted by `RetrievalService` after each retrieval attempt.
  - Purpose: show mode, confidence, failure code, and result count for retry/fallback diagnosis.
  - How it works: after chunks are returned, `RetrievalService` computes confidence, classifies the attempt as `NONE`, `RF-01`, `RF-02`, `RF-03`, or `RF-04`, then decides whether to proceed or try the next mode.

## Search events

- `retrieval.lexical_search`
  - Emitted by `DynamicSchemaRetriever` after lexical scoring.
  - Purpose: show lexical-only candidate count before semantic merge.
  - How it works: scores indexed schema chunks by exact token matches, schema reference matches, aliases, inferred schema references, join-path overlap, and mode-specific fuzzy matching.
  - Business rules: included when they exist in the indexed chunks. Lexical search scores schema chunks, business-rule chunks, and join-path chunks before semantic embedding search runs.
  - Ordering: runs before `retrieval.embedding.started`; however, semantic search is not skipped just because lexical search found results.
  - Difference from correction: correction rewrites or expands query terms before retrieval; lexical search uses those processed terms to score schema chunks. Lexical search does not call `pg_trgm`.

- `retrieval.semantic_search`
  - Emitted by `DynamicSchemaRetriever` as the semantic retrieval outcome.
  - Purpose: show whether semantic retrieval was skipped, succeeded, or failed.
  - How it works: checks semantic dependencies and active embeddings first; if available, it wraps the embedding call plus vector search and logs the final semantic result.
  - Skip reasons include missing semantic dependencies or no active embeddings.

- `retrieval.embedding.started`
  - Emitted before creating the query embedding.
  - Purpose: mark the embedding client call without logging the query text or embedding vector.
  - How it works: starts timing before calling `EmbeddingClient.embed(...)` with the processed retrieval query.

- `retrieval.embedding.completed`
  - Emitted after the query embedding call succeeds or fails.
  - Purpose: show embedding latency, model, dimension count, or failure code.
  - How it works: on success, logs latency and embedding dimension count; on failure, logs `EMBEDDING_QUERY_FAILED` and semantic retrieval returns no semantic chunks.

- `retrieval.vector_search.started`
  - Emitted before searching the vector repository.
  - Purpose: mark the pgvector/vector DB search with fingerprint, model, active embedding count, and limit.
  - How it works: starts timing after a query embedding is created, then calls `VectorRetrievalRepository.search(...)` with the query embedding, schema fingerprint, embedding model, and search limit.

- `retrieval.vector_search.completed`
  - Emitted after vector DB search succeeds or fails.
  - Purpose: show vector search latency, result count, top chunk IDs, or failure code.
  - How it works: on success, logs result count and top chunk IDs; on failure, logs `VECTOR_SEARCH_FAILED` and semantic retrieval falls back to lexical-only results.

- `retrieval.schema_search.completed`
  - Emitted by `DynamicSchemaRetriever` after lexical and semantic results are merged.
  - Purpose: summarize per-attempt schema search internals before `RetrievalService` calculates confidence.
  - How it works: merges lexical and semantic chunks by chunk ID, boosts duplicate matches, filters zero scores, sorts by score, limits to top chunks, and returns them to `RetrievalService`.

## Redundant events

- A second `retrieval.started` inside `RetrievalService` is redundant because `AgentOrchestrator` already owns retrieval boundary logging.
- `retrieval.schema_search.completed` is not a replacement for `retrieval.completed`; it is a lower-level per-attempt event.
- `retrieval.semantic_search status=OK` is a summary of embedding plus vector search, so dashboards may use either the detailed embedding/vector events or this summary depending on the required granularity.

## Vocabulary correction with embeddings

Vocabulary correction is still useful when semantic embedding retrieval is enabled, but it has a different role.

- Keep correction for schema/domain terms.
  - Purpose: help with misspellings, aliases, abbreviations, and database-specific words that the embedding model may not map reliably to the indexed schema chunks.
  - Example: `cust revnue` can be expanded toward `customer revenue` before lexical scoring and embedding.

- Do not rely on correction as the main semantic mechanism.
  - Purpose: embeddings already handle broad semantic similarity, so correction should not aggressively rewrite normal user language.
  - Example: `buyers` may semantically match `customers` even without correction.

- Treat ambiguous correction carefully.
  - Purpose: when one term may map to multiple schema/domain terms, retrieval should lower confidence or ask for clarification instead of pretending the correction is certain.
  - Example: `date` may refer to `order_date`, `payment_date`, or `created_at`.

- Current implementation applies correction before semantic search.
  - How it works: `RetrievalQueryProcessor` builds a `ProcessedQuery`; `DynamicSchemaRetriever` then embeds `ProcessedQuery.retrievalQuery()` for semantic search.
  - Effect: the embedding query can include normalized, corrected, alias, and analyzed terms.

In short: embeddings reduce the need for heavy correction, but they do not remove the need for lightweight schema-aware correction.

## Correction library choices

There is no generic spell-correction library that can fully replace schema-aware vocabulary for this project.

Generic correction libraries know language words. The retrieval problem here is different: it must understand database terms such as table names, column names, aliases, governed business-rule terms, and organization-specific abbreviations. Those terms still need to come from the schema index, governed rules, or an approved domain glossary.

Recommended approach:

- Generate the vocabulary automatically.
  - How it works: build correction terms from table names, column names, schema refs, chunk aliases, join paths, and governed business rules during index rebuild.
  - Effect: the vocabulary is not manually maintained term-by-term, but it remains schema-aware.

- Use PostgreSQL `pg_trgm` when the retrieval database is PostgreSQL.
  - How it works: store generated vocabulary terms in a table and use trigram similarity to find likely corrections.
  - Fit: good for typo correction, fuzzy matching, and schema/domain terms.

- Use Lucene spell checking if we want an embedded Java-only correction index.
  - How it works: build a Lucene term dictionary from generated schema/domain terms and use edit-distance suggestions.
  - Fit: useful when avoiding database-specific correction logic.

- Use SymSpell only if correction volume becomes a bottleneck.
  - How it works: precomputes delete variants of dictionary terms for fast lookup.
  - Fit: fast typo correction, but it still needs a schema/domain dictionary as input.

- Use Elasticsearch/OpenSearch suggesters only if search infrastructure is already part of the system.
  - How it works: index generated terms and query term/phrase suggesters.
  - Fit: powerful, but too heavy if retrieval otherwise only needs PostgreSQL plus embeddings.

Current best fit for this repository: keep `pg_trgm` or in-memory correction, but make sure the vocabulary is generated from schema/index data rather than manually curated one word at a time.

## Correction vs lexical search

Correction and lexical search are separate steps.

- Correction asks: "Did the user type a term that should map to a known schema/domain term?"
  - PostgreSQL profile: this can call `pg_trgm` through `PostgresTrigramVocabularyService`.
  - Example: `revnue` may correct to `revenue`.

- Lexical search asks: "Which indexed schema chunks match the processed query terms?"
  - This is done in Java inside `DynamicSchemaRetriever`.
  - It scores schema chunks, business-rule chunks, and join-path chunks using token overlap, aliases, schema refs, join paths, and mode-specific fuzzy matching.
  - It does not call `pg_trgm`.
  - It runs before embedding/vector search, then lexical and semantic results are merged.

Example:

```text
User question: show cust revnue
normalize: show cust revnue
tokenize: [show, cust, revnue]
correction: cust -> customer, revnue -> revenue
processed query: show cust revnue customer revenue
lexical_search: scores schema/business-rule/join chunks that contain or reference customer/revenue terms
semantic_search: optionally embeds the processed query and searches vector chunks
```

## Retrieval database usage

Not every retrieval step uses a database. Some steps are pure Java, some use the retrieval database, and schema/index rebuild uses the datasource database.

- `retrieval.started`
  - Database used: none.
  - It only marks the retrieval boundary in application logs.

- `retrieval.query_processed`
  - Database used: maybe.
  - Normalize/tokenize/analyzed terms are pure Java through `TextNormalizer`.
  - Correction runs inside query processing; if the vocabulary provider is `postgres-trgm`, it queries the retrieval database table `retrieval_vocabulary`.
  - If the vocabulary provider is `in-memory`, it uses an in-memory term set.

- `retrieval.correction`
  - Database used: maybe retrieval database.
  - PostgreSQL profile: uses `retrieval_vocabulary` with `pg_trgm` similarity.
  - In-memory profile: uses in-memory vocabulary generated during index rebuild.

- `retrieval.lexical_search`
  - Database used: normally none during scoring.
  - It scores already-loaded `RetrievedChunk` objects in Java.
  - Those chunks may have been loaded from `retrieval_chunk_embedding` first when the app starts in consume-only/PostgreSQL mode and no in-memory current index exists.

- `retrieval.semantic_search`
  - Database used: maybe retrieval database.
  - It first checks whether semantic dependencies exist and whether active embeddings exist.
  - PostgreSQL/pgvector profile checks active records in `retrieval_chunk_embedding`.
  - In-memory vector profile checks in-memory vector records.

- `retrieval.embedding.started` / `retrieval.embedding.completed`
  - Database used: none.
  - External service used: embedding client, for example OpenAI in PostgreSQL/full profile or fake embedding in local profile.
  - It embeds the processed user query, not the business-rule chunks. Business-rule chunks are embedded during index rebuild.

- `retrieval.vector_search.started` / `retrieval.vector_search.completed`
  - Database used: retrieval database when `agent.retrieval.vector.provider=pgvector`.
  - PostgreSQL/pgvector profile searches `retrieval_chunk_embedding` using vector distance.
  - In-memory vector profile searches in-memory vectors.

- `retrieval.schema_search.completed`
  - Database used: none.
  - It merges lexical and semantic chunk results in Java.

- `retrieval.attempt_completed`
  - Database used: none.
  - It computes confidence/failure code from returned chunks in Java.

- `retrieval.completed`
  - Database used: none.
  - It logs the final retrieval result returned to the orchestrator.

Index rebuild database usage:

- Datasource database: read real schema metadata such as tables, columns, and foreign keys.
- Governance database: read approved/active business rules when business-rule source is `DATABASE`.
- Retrieval database: store generated vocabulary in `retrieval_vocabulary` and generated embeddings in `retrieval_chunk_embedding`.

## Source files

- `src/main/java/com/nlda/orchestrator/AgentOrchestrator.java`
- `src/main/java/com/nlda/retrieval/RetrievalService.java`
- `src/main/java/com/nlda/retrieval/impl/retriever/DynamicSchemaRetriever.java`
- `src/main/java/com/nlda/retrieval/impl/retriever/LexicalSchemaChunkScorer.java`
