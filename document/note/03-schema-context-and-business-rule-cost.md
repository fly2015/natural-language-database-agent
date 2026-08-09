# Schema Context And Business Rule Cost Note

## Purpose
Capture why the agent should retrieve selected schema context and governed business rules instead of sending all database schemas directly to the LLM.

## Why Not Feed All Schemas To The LLM
- Large schemas increase token cost, latency, and noise.
- The LLM may focus on irrelevant tables or columns.
- Full schema exposure can reveal data structures the user does not need.
- Schema metadata does not explain business meaning.
- Ambiguous terms need approved definitions, not LLM guesses.

Preferred flow:

```text
user question
-> retrieve relevant schema, join paths, and business rules
-> generate SQL
-> validate SQL against live metadata and policy
```

## Business Rule Cost
Maintaining business rules costs time and resources. This is normal for enterprise NL-to-SQL systems.

Business rules are needed for definitions that schema cannot safely express:
- what counts as active, cancelled, paid, revenue, churn, or top customer
- which date column is the business date
- which joins are preferred when multiple paths exist
- which tenant, security, or policy filters must apply

## Standard Practice
Business rules should be governed knowledge, not hardcoded Java logic.

Recommended maturity path:
1. Start with external config, for example `business-rules.yml`.
2. Move approved rules to a database or admin workflow.
3. Generate embeddings when rules change.
4. Use vector or hybrid search to retrieve rules by meaning.
5. Validate rule schema references against live metadata.
6. Disable or downgrade stale rules when schemas drift.

The cost is justified because governed rules make generated SQL more accurate, auditable, and adaptable across changing schemas.
