# Need To Clarify

## Retrieval And Schema Validation
- Define exact `schemaCoverage` rules.
- Decide whether retrieved schema chunks and vector chunks should be revalidated after retrieval, before prompt construction.
- Add a separate typed prompt-context validation layer.
- Validate join-path chunks explicitly, even when generated from foreign-key metadata.
- Decide how to handle curated join paths when databases do not have foreign keys.
- Decide whether business-rule schema refs should be excluded or downgraded when stale.

### Typed Prompt-Context Validation
Meaning:

```text
RetrievedChunk list
-> TypedPromptContext
-> PromptContextValidator
-> PromptBuilder
```

Purpose:
- validate the exact structured context before SQL generation
- prevent invalid retrieved context from reaching the LLM
- catch stale schema refs before prompt construction
- keep schema, business rules, join paths, policies, and dialect separate

Validator should check:
- every table exists
- every column exists
- every business-rule schema ref is valid
- every join path uses valid tables and columns
- schema fingerprint is current
- no forbidden schema/table is included
- required context is present for the user question
- no contradictory business rules are selected
- dialect is known

Current state:

```text
business-rule schema refs are checked during chunk building
generated SQL is checked after LLM
but the final typed context before prompt building is not separately validated yet
```

## Guardrail Hardening
- Decide whether current regex-based SQL validation is enough or whether a SQL parser/AST validator is required.
- Add provider-specific SQL validation for PostgreSQL, H2, MySQL, SQL Server, and Oracle.
- Add query-cost or explain-plan guardrails before execution.
