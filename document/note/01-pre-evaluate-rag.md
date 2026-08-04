# Pre-Evaluate RAG Note

## Purpose
Define how to evaluate retrieval context before sending context to SQL generation.

## Why This Exists
- Prevent low-quality context from causing SQL hallucinations.
- Reduce wasted LLM calls.
- Make retrieval behavior measurable and testable.

## Inputs
- user question
- extracted intent slots (metric, entity, time range, filters)
- top-k retrieval chunks with scores
- allowed schema policy

## Evaluation Signals
1. Similarity quality:
- max score
- mean score
- score drop between rank 1 and rank k

2. Schema coverage:
- does context include required entity?
- does context include metric columns?
- does context include time/filter columns?

3. Join path availability:
- can required entities be joined with known keys?

4. Policy compliance:
- all references are in allowed schemas/tables

5. Rule consistency:
- no contradictions between selected business rules

## Confidence Calculation (Reference)
confidence = 0.35 * similarity + 0.30 * coverage + 0.15 * joinPath + 0.10 * policy + 0.10 * consistency

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

## Test Checklist
- high-confidence context should proceed
- medium-confidence context should retry
- low-confidence context should clarify
- missing join path should not proceed
- forbidden schema reference should not proceed
