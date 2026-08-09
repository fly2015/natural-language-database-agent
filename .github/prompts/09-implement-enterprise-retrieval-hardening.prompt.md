---
description: "Harden retrieval to enterprise standard with typed context, metadata providers, schema drift validation, and typo-tolerant query processing"
name: "Implement Enterprise Retrieval Hardening"
argument-hint: "Optional focus, such as typed context, metadata provider abstraction, typo correction, schema drift, business rules, or retrieval scoring"
agent: "agent"
model: "GPT-5 (copilot)"
---
Implement enterprise-grade retrieval hardening for the natural language database agent.

Use these documents:
- [Retrieval Design](../../document/09-retrieval-design.md)
- [Text Normalization and Typo Correction](../../document/note/02-text-normalization-and-typo-correction.md)
- [Pre-Evaluate RAG Note](../../document/note/01-pre-evaluate-rag.md)
- [Dialect-Aware SQL Prompting Note](../../document/note/02-dialect-aware-sql-prompting.md)
- [Architecture Overview](../../document/04-architecture-overview.md)
- [Technology Stack](../../document/03-technology-stack.md)
- [Workspace Copilot Instructions](../copilot-instructions.md)

Out of scope for this prompt:
- enterprise memory/context implementation
- previous result references
- conversational follow-up resolution
- UI changes unless required for tests or wiring

Goal:
- Make retrieval dynamic, typed, externalized, traceable, and adaptable to schema changes.
- Do not rely on hardcoded table names, hardcoded aliases, hardcoded business rules, or domain-specific Java scoring.
- The SQL generator should receive only validated, relevant, typed context.

Tasks:
1. Add shared text normalization.
   - Create a reusable `TextNormalizer`.
   - Support lowercase, Unicode normalization, punctuation cleanup, whitespace cleanup, and stable tokenization.
   - Replace duplicated ad hoc normalization in retrieval classes.

2. Add retrieval query processing.
   - Create `RetrievalQueryProcessor`.
   - Produce a `ProcessedQuery` with original question, normalized text, tokens, corrected terms, aliases, and final retrieval query.
   - Keep typo correction deterministic and testable.
   - Use Apache Commons Text or a small local edit-distance implementation if adding the dependency is not appropriate.

3. Add schema vocabulary matching.
   - Create `SchemaVocabularyMatcher`.
   - Build vocabulary from current schema metadata: table names, column names, schema refs, and configured aliases.
   - Fuzzy-match user tokens against schema vocabulary with confidence scores.
   - Penalize fuzzy matches compared with exact matches.
   - Ask clarification when fuzzy correction is ambiguous.

4. Strengthen typed retrieval context.
   - Ensure retrieved context remains typed: `SCHEMA`, `BUSINESS_RULE`, `ALIAS`, `JOIN_PATH`, `POLICY`, and `DIALECT` where applicable.
   - Preserve chunk id, type, source, schema refs, aliases, score, and freshness metadata.
   - Do not merge schema facts and business rules into untraceable raw strings before pre-evaluation.

5. Add metadata provider abstraction.
   - Introduce `SchemaMetadataProvider`.
   - Keep H2/local support working.
   - Add or prepare provider-specific implementations for generic JDBC, H2, and PostgreSQL.
   - Normalize provider output into the existing internal schema metadata model.

6. Validate schema drift.
   - Compute and compare schema fingerprints.
   - Rebuild schema chunks when metadata changes.
   - Revalidate business-rule schema refs against the current snapshot.
   - Exclude or downgrade stale rules that reference missing tables or columns.
   - Log refresh and validation results.

7. Improve retrieval scoring and pre-evaluation.
   - Remove domain-specific boosts from generic retrieval code.
   - Score with configurable signals: exact token match, alias match, schema coverage, business-rule coverage, join-path coverage, policy compatibility, freshness, and fuzzy-match confidence.
   - Keep confidence thresholds configurable.
   - Return safe clarification when confidence remains too low after retry.

8. Prepare vector retrieval boundaries without requiring pgvector in default tests.
   - Add interfaces for embedding/vector retrieval if they are missing.
   - Keep fake/deterministic implementations for tests.
   - Do not make the default test suite depend on OpenAI, network access, PostgreSQL, or pgvector.

Required behavior:
- typo-tolerant retrieval can recover common schema/user vocabulary typos when confidence is high
- ambiguous typo corrections ask clarification instead of guessing
- schema chunks are generated from active datasource metadata
- business rules are loaded from external configuration or governed source, not Java literals
- stale business rules do not reach SQL generation as approved context
- join paths are generated from foreign keys when available
- retrieval output is typed and traceable through prompt construction
- low-confidence retrieval prevents SQL generation

Implementation constraints:
- keep changes well-structured and small enough to review
- preserve existing REST API response shape
- preserve existing H2 demo behavior
- keep local tests deterministic
- avoid broad rewrites unrelated to retrieval
- avoid hardcoded customer/order/product-specific logic in production retrieval code
- do not implement enterprise memory/context in this prompt

Tests to add or update:
- `TextNormalizer` normalizes casing, punctuation, Unicode, and whitespace
- `RetrievalQueryProcessor` emits original, normalized, corrected, and final retrieval query
- typo like `custmer` can resolve to `customer` with strong schema vocabulary confidence
- ambiguous typo returns clarification/low-confidence signal
- schema vocabulary is built from extracted metadata, not hardcoded terms
- renamed table/column is discoverable after schema refresh without code changes
- business rule with missing schema ref is excluded or downgraded
- generated join-path chunks come from foreign keys
- retrieval scoring has no domain-specific Java boosts
- prompt context receives typed validated context only
- default test suite passes without live PostgreSQL, pgvector, OpenAI, or network access

Deliverables:
- shared normalization component
- retrieval query processor
- schema vocabulary matcher
- metadata provider abstraction
- schema drift validation behavior
- typed retrieval scoring and confidence improvements
- deterministic tests covering typo recovery, ambiguity, drift, and typed context
