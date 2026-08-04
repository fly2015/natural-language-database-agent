# Pre-Evaluate RAG Note

## Purpose
Define how to evaluate retrieval context before sending context to SQL generation.

The agent should retrieve and evaluate trusted schema context before calling the LLM whenever a question needs database schema knowledge. The LLM should generate SQL only from schema chunks and business rules that were retrieved from the active datasource or from curated rule sources.

## Why This Exists
- Prevent low-quality context from causing SQL hallucinations.
- Reduce wasted LLM calls.
- Make retrieval behavior measurable and testable.
- Avoid asking the LLM to guess table names, column names, joins, or business metrics.
- Detect schema drift before SQL generation.

## Recommended Flow
1. Extract or refresh schema metadata from the active datasource.
2. Build schema chunks from tables, columns, primary keys, foreign keys, nullable flags, and data types.
3. Add curated business-rule chunks separately from physical schema chunks.
4. Retrieve top-k chunks for the user question.
5. Pre-evaluate retrieval confidence and schema coverage.
6. Call the LLM only when retrieval passes the confidence threshold.
7. Validate generated SQL against the live schema catalog before execution.

This gives two layers of protection:
- retrieval gate before LLM generation
- SQL guardrail validation after LLM generation

## Inputs
- user question
- extracted intent slots (metric, entity, time range, filters)
- top-k retrieval chunks with scores
- allowed schema policy
- current schema fingerprint
- retrieved schema refs (tables and joined tables)
- retrieved business-rule refs and aliases

## Evaluation Signals
1. Similarity quality:
- max score
- mean score
- score drop between rank 1 and rank k

2. Schema coverage:
- does context include required entity?
- does context include metric columns?
- does context include time/filter columns?
- does context include candidate grouping columns?
- does context include enough column metadata for explicit SELECT lists?

3. Join path availability:
- can required entities be joined with known keys?
- are join keys present in retrieved chunks?
- are many-to-many bridge tables included when needed?

4. Policy compliance:
- all references are in allowed schemas/tables

5. Rule consistency:
- no contradictions between selected business rules

6. Schema freshness:
- schema fingerprint matches the current datasource metadata
- stale chunks trigger refresh/rebuild before retry

## Confidence Calculation (Reference)
confidence = 0.30 * similarity + 0.30 * coverage + 0.15 * joinPath + 0.10 * policy + 0.10 * consistency + 0.05 * freshness

All dimensions should be normalized between 0 and 1.

## Decision Thresholds
- confidence >= 0.75: PROCEED
- 0.55 <= confidence < 0.75: RETRY
- confidence < 0.55: CLARIFY

## Actions by Decision
- PROCEED:
  - continue to prompt builder and SQL generation

- RETRY:
  1. normalized query retrieval
  2. synonym/alias expansion retrieval
  3. hybrid retrieval (vector + keyword)

- CLARIFY:
  - return NEEDS_CLARIFICATION response to UI
  - ask for missing fields (metric, timeframe, entity)

## Dynamic Schema Adaptation
Schema chunks should be generated from the active datasource, not handwritten for one demo schema.

Expected metadata:
- table name
- column name
- data type
- nullable flag
- primary key columns
- foreign key relationships
- schema fingerprint/hash

Expected chunk types:
- table chunk: one chunk per physical table with columns, keys, and outbound joins
- business-rule chunk: one chunk per curated metric, alias, or domain rule
- join-path chunk, optional later: one chunk for frequently used multi-table paths

When schema changes:
- compute a new schema fingerprint
- rebuild schema chunks
- keep previous chunks only as fallback cache
- log the refresh event with fingerprint and chunk count

## Alias and Scoring Guidance
Avoid hardcoded domain scoring such as customer/order/product-specific checks in generic retrieval code.

Temporary hardcoded heuristics, for example `matchesSchemaRef(...)` with fixed terms, are acceptable only as an early bootstrap fallback. They are not flexible enough for schema drift or different customer databases.

Preferred scoring should be metadata-driven:
- tokenize the user question
- tokenize table names, column names, and foreign-key names
- compare normalized singular/plural variants
- match configured aliases from curated business rules
- score exact token matches higher than substring matches
- boost chunks that satisfy required entity, metric, time, filter, and join coverage

Later pgvector-backed retrieval can replace or augment lexical scoring, but the same pre-evaluation gate should remain.

## Typo Handling Guidance
Typos should reduce retrieval confidence, but they should not immediately cause clarification.

Recommended typo-tolerant flow:
1. Normalize casing, punctuation, and whitespace.
2. Try exact token matching against schema terms and curated aliases.
3. Try singular/plural and simple stemming variants.
4. Try fuzzy matching with a small edit-distance threshold for table, column, and alias names.
5. Apply a confidence penalty for fuzzy matches compared with exact matches.
6. Retry with expanded aliases or hybrid retrieval when confidence is medium.
7. Clarify only when recovery still cannot produce enough trusted schema coverage.

Examples:
- `custmer revnue 2026` can recover to customer/revenue if retrieved chunks cover customer and revenue-related schema.
- `costmer 2026` may need clarification if it could mean customer, cost, cost center, or another domain term.

Clarification is appropriate when:
- fuzzy matches produce multiple likely schema candidates
- required entity or metric coverage remains missing
- no valid join path can be established
- confidence remains below the threshold after retries

Implementation guidance:
- keep typo tolerance inside retrieval/evaluation, not SQL generation
- never allow the LLM to invent corrected schema names
- include the corrected candidate terms in trace/debug logs
- prefer asking a clarification question over generating SQL from uncertain fuzzy matches

## Output Contract (Example)
{
  "decision": "PROCEED | RETRY | CLARIFY",
  "confidence": 0.68,
  "reasons": ["missing time field", "weak alias mapping"],
  "missingFields": ["timeRange"],
  "missingSchema": ["order_header.order_date"],
  "traceId": "req-123"
}

## Logging Requirements
Log for each attempt:
- traceId
- attempt number
- retriever mode
- confidence value
- reason list
- final decision
- schema fingerprint
- chunk count
- refresh/rebuild flag
- fallback usage flag

## Test Checklist
- high-confidence context should proceed
- medium-confidence context should retry
- low-confidence context should clarify
- missing join path should not proceed
- forbidden schema reference should not proceed
- schema fingerprint change should rebuild chunks
- renamed table/column should be discoverable without code changes
- curated aliases should improve retrieval without hardcoding in retriever logic
- small typo with strong schema coverage should recover
- ambiguous typo with multiple candidate meanings should clarify
- fuzzy matches should score lower than exact schema or alias matches
