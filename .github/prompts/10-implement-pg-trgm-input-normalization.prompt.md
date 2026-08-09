---
description: "Implement Lucene normalization and PostgreSQL pg_trgm typo correction before retrieval"
name: "Implement pg_trgm Input Normalization"
argument-hint: "Optional focus, such as Lucene analyzer, schema vocabulary table, pg_trgm migration, H2 fallback, or ambiguity handling"
agent: "agent"
model: "GPT-5 (copilot)"
---
Implement enterprise input normalization and typo correction before the retrieval process.

Use these documents:
- [Retrieval Design](../../document/09-retrieval-design.md)
- [Text Normalization and Typo Correction](../../document/note/02-text-normalization-and-typo-correction.md)
- [Enterprise Agent Memory And Context Note](../../document/note/04-enterprise-agent-memory-context.md) for context only; do not implement memory in this prompt
- [Architecture Overview](../../document/04-architecture-overview.md)
- [Technology Stack](../../document/03-technology-stack.md)
- [Workspace Copilot Instructions](../copilot-instructions.md)

Goal:
- Normalize user input with a proven Java text-analysis library.
- Correct likely typos before retrieval using PostgreSQL `pg_trgm` when PostgreSQL is active.
- Keep the typo-correction source dynamic, indexed, and generated from schema metadata, aliases, and approved business rules.
- Do not let the LLM invent corrected table names, column names, aliases, or business terms.

Out of scope:
- pgvector semantic retrieval
- enterprise memory/context
- previous result references
- SQL generation prompt changes unless required to pass typed context

Required design:
```text
raw user question
-> TextNormalizer using Lucene Analyzer
-> tokenize/stem/remove noise
-> VocabularyCorrectionService
-> corrected/expanded retrieval query with confidence
-> retrieval pipeline
```

Tasks:
1. Keep or introduce `TextNormalizer`.
   - Use Lucene Analyzer or equivalent enterprise-grade library.
   - Normalize Unicode, casing, punctuation, whitespace, stop words, tokens, and stems.
   - Keep behavior deterministic in tests.

2. Replace temporary custom typo matching with an interface.
   - Add `VocabularyCorrectionService`.
   - Return structured correction candidates with original token, corrected token, source type, score, and ambiguity flag.
   - Avoid domain-specific Java literals.

3. Add PostgreSQL `pg_trgm` implementation.
   - Add migration for `CREATE EXTENSION IF NOT EXISTS pg_trgm`.
   - Add a vocabulary table generated from schema metadata, aliases, business rules, and join path labels.
   - Add trigram indexes for fast similarity search.
   - Search with `similarity(...)`, `%`, or configurable thresholds.
   - Prefer exact matches over fuzzy matches.
   - Mark correction as ambiguous when multiple candidates have close scores.

4. Keep local/H2 fallback.
   - H2 tests must not require PostgreSQL extensions.
   - Provide an in-memory deterministic implementation for tests and local demo.
   - Make provider selection configuration-driven.

5. Integrate before retrieval.
   - `RetrievalQueryProcessor` should call `VocabularyCorrectionService`.
   - Use corrected tokens to build the final retrieval query.
   - Penalize fuzzy corrections compared with exact tokens.
   - Ask clarification or return low confidence when correction is ambiguous.

6. Add governance metadata.
   - Vocabulary rows should include source type, source id, datasource id, schema fingerprint, term, normalized term, active flag, and updated timestamp where practical.
   - Rebuild vocabulary when schema fingerprint changes.
   - Exclude stale vocabulary entries from active retrieval.

Required behavior:
- `custmer` can correct to `customer` or `customers` when the active schema contains that vocabulary.
- `regoin` can correct to `region`.
- ambiguous typo correction lowers confidence and asks clarification instead of guessing.
- vocabulary is derived from live schema metadata and external business rules, not hardcoded Java lists.
- H2/default tests pass without PostgreSQL, network, OpenAI, or pgvector.

Implementation constraints:
- do not build a custom search engine in Java
- keep PostgreSQL-specific SQL isolated behind repository/service boundaries
- preserve existing REST response shape
- preserve typed retrieval context
- preserve audit logging and add correction trace logs without leaking sensitive data

Tests to add or update:
- Lucene normalization and stemming
- vocabulary generation from schema metadata
- vocabulary generation from external aliases/business rules
- PostgreSQL repository SQL is isolated and covered where practical
- H2 fallback correction is deterministic
- typo correction improves retrieval confidence
- ambiguous correction prevents unsafe SQL generation
- schema fingerprint change rebuilds vocabulary
- no domain-specific production Java scoring or vocabulary literals

Deliverables:
- `VocabularyCorrectionService` interface
- PostgreSQL `pg_trgm` vocabulary repository
- H2/in-memory fallback implementation
- migration/schema for vocabulary and trigram indexes
- integration into `RetrievalQueryProcessor`
- tests proving typo correction happens before retrieval
