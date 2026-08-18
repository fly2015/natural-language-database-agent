# Agentic Agent Terminology

## Purpose
Living note for learning and maintaining terminology used in this NL-to-SQL agent.

Use this as a glossary. Add new terms under the closest related group, or create a new group when a term does not fit.

## 1. Agent And Flow

### Agentic agent
An application that can use tools, memory, retrieval, planning, validation, and execution steps to complete a user goal, instead of only returning one direct model response.

In this project, the agent receives a natural-language database question, retrieves trusted context, generates SQL, validates the SQL, executes it, and returns a structured answer.

### Main flow
The user-facing query path from question to answer.

Typical flow:

```text
user question
-> input normalization
-> retrieval
-> SQL generation
-> guardrail validation
-> SQL execution
-> response
```

The main flow should not manage business-rule approval or admin governance. It should only consume approved, effective context.

### Governance flow
The admin/operator path for managing trusted data used by the agent.

Examples:
- create or update business rules
- approve or deactivate rules
- manage versions and owners
- refresh schema index
- rebuild embeddings
- inspect audit and retrieval diagnostics

Governance flow and main flow should be organized separately.

## 2. Retrieval And RAG

### RAG
Retrieval-Augmented Generation. The system retrieves relevant trusted context before asking the LLM to generate an answer or SQL.

In this project, RAG helps the SQL generator use current schema, business rules, aliases, and join paths.

### Retrieval
The step that finds relevant context for a user question.

Retrieval can use:
- lexical search
- typo/fuzzy search
- vector search
- schema reference validation
- ranking and confidence scoring

### Retrieval context
The selected information passed toward SQL generation.

Examples:
- table and column details
- business rules
- aliases
- join paths
- policy or dialect guidance

### Typed chunk
A small retrievable unit with a known kind, such as `SCHEMA`, `BUSINESS_RULE`, or `JOIN_PATH`.

Typed chunks are better than plain text snippets because the system can validate and audit what kind of context was used.

### Business-rule chunk
A retrievable chunk created from an approved business rule.

Example:

```text
Revenue means SUM(orders.total_amount) unless product-level revenue is requested.
```

The chunk should keep metadata such as rule id, version, schema refs, datasource, tenant, approval status, and source.

## 3. Business Rules And Governance

### Business rule
A governed definition that tells the agent how the business interprets data.

Examples:
- "Revenue means SUM(orders.total_amount)."
- "Undelivered orders means orders.status <> 'DELIVERED'."
- "Active customer means customers.status = 'ACTIVE'."

Business rules should not be hardcoded in Java for enterprise use.

### Governed business-rule table
Database tables that store business rules as managed enterprise data.

Typical fields:
- rule id
- text/content
- aliases
- schema references
- owner
- version
- approval status
- effective dates
- datasource
- tenant
- active flag

These tables are the source of truth for production-grade business rules.

### YAML business rules
Business rules stored in a YAML file.

YAML is useful for:
- local development
- tests
- demos
- bootstrap/seed data
- simple deployments where file review is acceptable

For stricter enterprise governance, database-backed rules are preferred.

### Approval status
The lifecycle state of a governed rule.

Common statuses:
- draft
- approved
- rejected
- inactive

The retrieval main flow should use only approved, active, currently effective rules.

### Effective dates
The date range where a rule is valid.

Example:

```text
effective_start = 2026-01-01
effective_end = 2026-12-31
```

This prevents old or future rules from being used at the wrong time.

## 4. Schema And Join Context

### Schema metadata
Information about database structure.

Examples:
- table names
- column names
- data types
- primary keys
- foreign keys
- nullable flags

### Schema chunk
A retrievable chunk created from schema metadata.

Example:

```text
table orders has columns id, customer_id, order_date, total_amount, status
```

### Join path
A known relationship path between tables.

Example:

```text
customers -> orders -> order_items -> products
```

A join path helps the SQL generator know how tables connect.

### Join-path chunk
A retrievable chunk that describes a join path and its keys.

Example:

```text
join path: customers -> orders
keys: orders.customer_id = customers.id
```

Join-path chunks can be generated from foreign keys or curated manually for legacy schemas.

### Schema reference
A reference from a rule or chunk to a specific table or column.

Example:

```text
orders.total_amount
```

Schema references must be validated against the active schema so stale rules do not reach SQL generation.

### Schema drift
When the database structure changes over time.

Examples:
- column renamed
- table removed
- new foreign key added
- data type changed

Schema drift should trigger validation and index rebuilds.

## 5. Embeddings And Vector Search

### Embedding
A numeric vector representation of text meaning.

Example text:

```text
Revenue means SUM(orders.total_amount)
```

The embedding model converts it into a vector that can be compared with other vectors.

### Embedding model
The model that converts text into embeddings.

In this project, the embedding layer should be behind an interface so the app can use:
- fake deterministic embeddings for tests
- OpenAI embeddings for real semantic search
- other providers later if needed

### Canonical text
The stable text representation used to create an embedding.

For a business rule, canonical text may combine:
- rule name
- rule text
- aliases
- schema refs
- datasource or tenant metadata

Canonical text should be stable because changes to it affect the content hash and whether embeddings need to be rebuilt.

### Vector database
A database or storage layer optimized for vector similarity search.

It is used as a search index, not as the source of truth.

### pgvector
A PostgreSQL extension for storing and searching vectors.

In this project:
- governed business-rule tables are the source of truth
- `pgvector` stores embeddings for semantic retrieval
- query-time vector search finds similar chunks

### In-memory vector store
A local/test vector repository that stores embeddings in memory.

Useful for:
- H2/default mode
- deterministic tests
- demos without PostgreSQL

### Vector search
Search that compares embeddings by semantic similarity.

Example:

```text
Question: top customers by spending
Rule: Revenue means SUM(orders.total_amount)
```

Vector search can match these even if the words are not exactly the same.

### Hybrid retrieval
Retrieval that combines multiple matching methods.

Examples:
- exact keyword match
- fuzzy typo match
- alias match
- vector similarity
- schema coverage

The final result is merged and ranked.

## 6. Indexing And Rebuilds

### Indexing
The process of preparing searchable data before query-time retrieval.

For this project:

```text
schema metadata
business rules
join paths
-> typed chunks
-> vocabulary
-> embeddings
-> vector index
```

### Rebuild index
The operation that refreshes retrieval support data.

It may rebuild:
- schema chunks
- business-rule chunks
- join-path chunks
- vocabulary
- embeddings

### Re-embedding
Generating embeddings again when canonical text, embedding model, schema fingerprint, or rule metadata changes.

### Content hash
A hash of the canonical text or indexed content.

It helps detect whether a chunk changed and whether the embedding must be rebuilt.

### Schema fingerprint
A stable fingerprint of the active schema snapshot.

If the schema fingerprint changes, the system should revalidate schema refs and rebuild affected chunks/embeddings.

## 7. Prompt And SQL Generation

### Prompt context
The selected trusted context formatted for SQL generation.

It should include only validated context that the agent is allowed to use.

### TypedPromptContext
A structured prompt input object that keeps context separated by type.

Example groups:
- schema
- business rules
- aliases
- join paths
- policy
- dialect
- confidence
- chunk ids

### PromptContextValidator
A component that validates prompt context before SQL generation.

It should prevent stale, unapproved, or incompatible context from reaching the LLM.

### SQL generation
The step where the system converts the user question plus trusted context into SQL.

### SQL guardrail
Validation that checks generated SQL before execution.

Examples:
- reject destructive SQL
- reject multiple statements
- reject `SELECT *`
- validate table and column references
- apply row limits
- enforce tenant/security policy

## 8. Observability And Audit

### Audit log
A record of important actions and decisions.

For query flow, useful audit fields include:
- question
- generated SQL
- retrieved chunk ids
- rule ids
- vector scores
- correction candidates
- prompt version
- model
- guardrail decision
- trace id

### Trace id
A correlation id used to connect logs from the same request or operation.

### Retrieval diagnostics
Admin/debug information that explains why the system selected or rejected context.

Examples:
- normalized query
- corrected query
- candidate chunks
- ranking scores
- vector scores
- failure codes
- confidence

## 9. Infrastructure

### H2
An embedded relational database often used for local development and tests.

In this project, H2 is useful for fast default test runs.

### PostgreSQL
A production-grade relational database.

In this project, PostgreSQL supports enterprise-style operation with extensions such as `pg_trgm` and `pgvector`.

### Docker Compose
A local tool for running services such as PostgreSQL in containers.

In this project, Docker Compose can run the PostgreSQL service used for production-like testing.

### Flyway
A database migration tool.

Flyway manages schema changes as versioned migration files instead of relying only on ad hoc SQL initialization.

## 10. Terms To Add Later

Use this template:

```text
### Term name
Short definition.

Why it matters in this project:
- point 1
- point 2
```
