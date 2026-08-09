# Retrieval Design

## 1. Goal
Design an enterprise-grade retrieval subsystem for the natural-language database agent.

The target principle is:

```text
Except for the user question, all context used for SQL generation must be dynamic, externalized, governed, and adaptable to schema changes.
```

This means retrieval must not depend on hardcoded table names, hardcoded business rules, hardcoded aliases, or hardcoded domain-specific scoring in Java code.

## 2. Current Problem
The current implementation is useful for a demo, but it is not yet enterprise-ready.

Known issues:
- Physical schema metadata is extracted dynamically, but retrieval scoring still contains domain-specific logic.
- Business rules are represented as curated Java code instead of external governed data.
- Schema chunks and business-rule chunks are returned as the same simple chunk shape.
- Alias expansion is hardcoded.
- Join paths are only indirectly present through foreign-key text.
- There is no lifecycle for business-rule ownership, approval, versioning, expiration, or schema-drift validation.
- There is no strong distinction between physical schema facts, semantic rules, join paths, policies, and final prompt context.

## 3. Required Context Types
Retrieval should produce typed context, not one undifferentiated list of strings.

Recommended context types:
- `SCHEMA`: tables, columns, primary keys, foreign keys, data types, nullable flags.
- `BUSINESS_RULE`: metric definitions, business meanings, calculation rules, filters.
- `ALIAS`: user vocabulary mapped to schema or business concepts.
- `JOIN_PATH`: known paths between tables and their join keys.
- `POLICY`: allowed schemas, denied objects, tenant/user visibility rules.
- `DIALECT`: provider-specific SQL rules such as PostgreSQL, H2, MySQL, Oracle, SQL Server.

These can be merged later for prompt construction, but they should remain typed and traceable.

## 4. Join/Path Context
Join/path context tells the system how tables connect.

Example physical relationships:

```text
customers.id -> orders.customer_id
orders.id -> order_items.order_id
products.id -> order_items.product_id
```

For a question like:

```text
Which products generated the highest revenue by customer region?
```

The required join path is:

```text
customers -> orders -> order_items -> products
```

A join-path chunk could be:

```text
join path: customers -> orders -> order_items -> products
keys:
orders.customer_id = customers.id
order_items.order_id = orders.id
order_items.product_id = products.id
supports: product revenue by customer, product revenue by customer region
```

Join paths should be generated dynamically from foreign keys when available. For legacy databases with missing or weak foreign keys, curated join paths should be stored externally and approved like business rules.

## 5. Business Rules
Business rules should not be hardcoded in Java.

Business rules should come from a governed source of truth:
- PostgreSQL tables
- configuration service
- admin UI
- data catalog
- versioned YAML seed for local/demo only

Examples:

```text
Revenue means SUM(orders.total_amount) unless product-level revenue is requested.
```

```text
Undelivered orders means orders.status <> 'DELIVERED'.
```

Business-rule records should include:
- id
- name
- rule text
- rule type: metric, filter, alias, join hint, policy
- linked schema refs
- aliases
- owner
- approval status
- version
- effective date range
- datasource id
- tenant id, if needed
- content hash
- embedding model/version, if embedded

## 6. Embeddings And Vector Search
An embedding is a numeric vector that represents the meaning of text.

Example text:

```text
Revenue means SUM(orders.total_amount)
```

The embedding model converts it into a vector:

```text
[0.012, -0.883, 0.441, ...]
```

At query time, the user question is also embedded. Vector search finds semantically similar rules even when the exact words differ.

Example:

```text
Question: Show top customers by spending
Rule: Revenue means SUM(orders.total_amount)
```

With exact SQL search, this may not match unless `spending` is stored as an alias.
With vector search, `spending`, `sales`, and `revenue` can be close in meaning.

Use both normal DB lookup and vector search:

```text
normal tables = source of truth and governance
pgvector = semantic search index
```

Do not use the vector database as the only source of truth.

## 7. Embedding Generation Flow
Embeddings are generated during indexing, not during SQL generation.

Business-rule indexing:

```text
approved business rule
  -> build canonical text
  -> call embedding model
  -> receive vector
  -> store vector in pgvector with rule id and metadata
```

Question-time retrieval:

```text
user question
  -> call embedding model
  -> receive question vector
  -> pgvector similarity search
  -> top-k approved business rules
```

With LangChain4j, the embedding client would use an embedding model such as:

```text
OpenAiEmbeddingModel
model: text-embedding-3-small or configured equivalent
```

The embedding layer should be behind an interface:

```text
EmbeddingClient
  -> LangChain4jOpenAiEmbeddingClient
  -> FakeEmbeddingClient for tests
```

## 8. Storage Model
Recommended source-of-truth tables:

```text
business_rule
business_rule_version
business_rule_schema_ref
business_rule_alias
business_rule_embedding
curated_join_path
curated_join_path_edge
```

Recommended schema index tables:

```text
schema_snapshot
schema_table
schema_column
schema_foreign_key
schema_chunk_embedding
```

The vector tables should include metadata filters:
- tenant id
- datasource id
- schema fingerprint
- rule status
- rule version
- embedding model
- content hash

## 9. Metadata Providers
The system should support multiple database metadata providers.

Use a stable interface:

```text
SchemaMetadataProvider
  -> dialect()
  -> extract()
```

Initial providers:
- `JdbcGenericSchemaMetadataProvider`
- `PostgresSchemaMetadataProvider`
- `H2SchemaMetadataProvider`

Future providers:
- `MySqlSchemaMetadataProvider`
- `OracleSchemaMetadataProvider`
- `SqlServerSchemaMetadataProvider`

Why provider-specific metadata matters:
- catalogs and schemas are modeled differently
- type names differ
- foreign-key metadata differs
- quoting rules differ
- date/time functions differ
- limit/pagination syntax differs

All providers should normalize into one internal `SchemaMetadataSnapshot`.

## 10. Retrieval Pipeline
Recommended pipeline:

```text
1. Receive user question.
2. Normalize/tokenize question.
3. Detect active datasource and SQL dialect.
4. Ensure schema metadata index is current.
5. Retrieve schema candidates.
6. Retrieve business-rule candidates.
7. Retrieve alias candidates.
8. Retrieve generated and curated join paths.
9. Merge typed candidates.
10. Validate schema refs against current snapshot.
11. Rerank with deterministic coverage signals.
12. Pre-evaluate confidence.
13. Send approved typed context to prompt builder.
```

The SQL generator should never receive unvalidated or stale context.

## 11. Retrieval Scoring
Retrieval scoring should not contain domain-specific Java logic like customer/order/product boosts.

Recommended scoring signals:
- vector similarity
- exact table/column token match
- alias match
- business-rule match
- schema-ref coverage
- metric coverage
- time/filter coverage
- join-path availability
- rule approval status
- schema freshness
- policy compatibility

Example weighted score:

```text
confidence =
  0.25 semanticSimilarity
+ 0.20 schemaCoverage
+ 0.15 businessRuleCoverage
+ 0.15 joinPathCoverage
+ 0.10 exactAliasMatch
+ 0.10 policyCompliance
+ 0.05 freshness
```

Weights should be configurable and tested.

## 12. Schema Drift Handling
Schema drift must affect retrieval confidence.

When schema changes:
- compute a new schema fingerprint
- rebuild schema chunks
- revalidate business-rule schema refs
- disable or downgrade stale rules
- rebuild embeddings for changed canonical text
- keep old chunks only as fallback cache
- log refresh/revalidation results

If an approved rule references a removed column, the system should not use that rule for SQL generation until it is repaired or reapproved.

## 13. Prompt Context Contract
The prompt builder should receive typed context, not raw mixed strings.

Example prompt context:

```json
{
  "schema": [],
  "businessRules": [],
  "joinPaths": [],
  "dialect": "postgresql",
  "confidence": 0.84,
  "missing": []
}
```

The prompt builder can format this into compact text for the LLM, but it should preserve:
- source type
- id
- version
- schema refs
- confidence

## 14. Guardrails
Retrieval must cooperate with SQL guardrails.

Required validations:
- generated SQL references only known tables and columns
- generated SQL uses only approved schemas
- generated SQL follows active dialect rules
- generated SQL is read-only
- generated SQL is one statement
- generated SQL has a safe limit
- generated SQL does not use stale business rules

Repair prompts should include only:
- retrieved typed context
- guardrail validation errors
- active dialect
- previous rejected SQL

## 15. Phased Implementation Plan
Phase 1: Typed context model
- Add chunk type/kind.
- Separate schema chunks, business-rule chunks, and join-path chunks.
- Remove hardcoded domain scoring.
- Keep existing H2 demo working.

Phase 2: Externalized business rules
- Move demo rules from Java to configuration or seed data.
- Add business-rule validation against current schema.
- Add tests for renamed/missing schema refs.

Phase 3: Metadata provider abstraction
- Introduce provider interface.
- Keep H2 and generic JDBC support.
- Add PostgreSQL-specific provider.

Phase 4: Vector retrieval
- Add embedding client interface.
- Add pgvector repository.
- Generate embeddings for approved rules and schema chunks.
- Add fake embedding model for deterministic tests.

Phase 5: Enterprise governance
- Add rule status/version/owner/effective dates.
- Add admin/API workflow later.
- Add audit logs for retrieved rule ids and schema fingerprints.

## 16. Test Strategy
Required tests:
- schema extraction works for H2
- schema extraction provider can be swapped
- business rules are loaded externally, not from Java literals
- renamed table/column does not require code changes
- stale business rule is excluded or downgraded
- alias lookup works from external rule data
- vector search returns semantically related rules
- exact alias lookup and vector lookup are merged safely
- join path is generated from foreign keys
- curated join path works when FK metadata is missing
- unknown schema refs do not reach SQL generation
- low retrieval confidence asks clarification
- prompt builder receives typed context only

## 17. Open Decisions
- Source of truth for business rules: PostgreSQL tables, YAML seed, external catalog, or admin UI?
- Embedding provider: OpenAI only, or pluggable providers?
- Vector scope: business rules only first, or schema chunks too?
- Multi-tenant scope: needed now or later?
- Rule approval workflow: required for first enterprise version or later?
- Schema refresh trigger: startup, schedule, manual endpoint, or datasource migration event?
