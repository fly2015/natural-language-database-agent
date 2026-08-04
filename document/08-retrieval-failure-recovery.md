# Retrieval Failure Recovery Playbook

## 1. Purpose
Define how the agent behaves when retrieval fails or returns weak context.

## 2. Failure Types
- RF-01: No chunks retrieved.
- RF-02: Retrieved chunks have low confidence/relevance.
- RF-03: Retrieval timeout or vector service unavailable.
- RF-04: Retrieval succeeds, but generated SQL fails validation repeatedly.

## 3. Recovery Pipeline
1. Detect failure type and record failure code.
2. Retry retrieval with normalized query text.
3. Retry retrieval with expanded terms (synonyms and business aliases).
4. Switch to hybrid retrieval (vector + keyword).
5. Fallback to curated schema dictionary and business rules cache.
6. If still weak context, ask user for targeted clarification.
7. Stop and fail safely if confidence is still below threshold.

## 4. Retry Policy
- Maximum retrieval attempts: 3.
- Recommended backoff: 150 ms, 300 ms.
- Confidence gate before SQL generation: configurable threshold (example: 0.65).

## 4A. Pre-LLM Context Evaluation
Evaluate retrieval context before SQL generation with deterministic signals.

Recommended signals:
- similarity score quality (max, mean, score gap)
- coverage of required schema elements (entity, metric, time, filters)
- join-path availability across required tables/views
- policy compliance (allowed schemas only)
- business-rule consistency across selected chunks

Decision policy:
- confidence >= 0.75: proceed to LLM
- 0.55 <= confidence < 0.75: retry retrieval path
- confidence < 0.55: ask clarification and safe stop

Evaluator output (example):
{
	"decision": "PROCEED | RETRY | CLARIFY",
	"confidence": 0.68,
	"reasons": ["missing time dimension", "weak alias match"],
	"missingFields": ["timeRange"],
	"missingSchema": ["order_header.order_date"]
}

## 5. Clarification Strategy
Ask only missing dimensions:
- metric
- time range
- entity (customer/order/product/region)

Example:
Please clarify the metric and timeframe. For example: total revenue in Q1 2026 by region.

## 6. Safe Failure Response
The system could not retrieve enough trusted schema context to generate a safe SQL query. Please clarify your request by metric, period, and target entity.

## 7. Observability
For each attempt, log:
- traceId
- retrieval attempt number
- retriever mode (vector, hybrid, fallback)
- top-k score summary
- failure code and decision

## 8. Testing
- Simulate empty vector index.
- Simulate retriever timeout.
- Simulate low confidence retrieval.
- Simulate schema drift causing invalid references.
- Verify evaluator threshold transitions (proceed, retry, clarify).
Expected: no unsafe SQL execution and clear user guidance.
