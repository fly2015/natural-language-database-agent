# Dialect-Aware SQL Prompting Note

## Purpose
Define how SQL generation prompts should adapt to the target database provider.

The SQL generator should not rely on one global prompt for every database. Prompt rules should be selected from the active SQL dialect so generated SQL is valid, performant, and testable for that provider.

## Why This Exists
- PostgreSQL, H2, MySQL, SQL Server, and Oracle have different function syntax.
- A query that runs in PostgreSQL may fail in H2 local tests.
- Some valid SQL is still undesirable for performance, for example wrapping indexed date columns in functions.
- Provider-specific prompt rules are easier to test and maintain than ad hoc LLM repair after failures.

## Recommended Design
Add a dialect configuration:

```yaml
agent:
  sql:
    dialect: ${AGENT_SQL_DIALECT:postgresql}
```

Suggested dialect values:
- `postgresql`
- `h2-postgresql`
- `mysql`
- `sqlserver`
- `oracle`
- `generic`

Prompt building should use this setting:

```text
SqlPromptBuilder
  -> common safety rules
  -> common schema rules
  -> dialect-specific SQL rules
```

## Common Rules For All Dialects
- Generate exactly one read-only `SELECT`.
- Never generate write or destructive SQL.
- Do not generate multiple statements.
- Do not use `SELECT *`.
- Put only real table names after `FROM` and `JOIN`.
- Use only retrieved schema chunks and curated business rules.
- Include a row limit unless the user asks for fewer rows.
- Prefer clarification or rejection over guessing.

## PostgreSQL Rules
- Use `LIMIT n`.
- Use `DATE 'YYYY-MM-DD'` for date literals.
- Prefer half-open date ranges for year filters:

```sql
WHERE o.order_date >= DATE '2026-01-01'
  AND o.order_date < DATE '2027-01-01'
```

- Avoid filtering with `EXTRACT(YEAR FROM date_column) = 2026` when a date range can express the same condition.
- Avoid `TO_CHAR`, `FORMAT`, or other formatting functions in `WHERE` for indexed date columns.
- Use `ILIKE` only when case-insensitive text matching is explicitly needed.

## H2 PostgreSQL Mode Rules
- Keep local/demo SQL compatible with H2 PostgreSQL mode.
- Prefer the same half-open date range style used for PostgreSQL.
- Avoid PostgreSQL-only functions unless tests prove H2 supports them.
- Use H2-specific functions only in deterministic test fixtures, not production prompts.

## Generic Date Filtering Rule
For natural-language questions like "in 2026", prefer converting the year into a concrete date range instead of applying functions to the date column.

Preferred:

```sql
WHERE order_date >= DATE '2026-01-01'
  AND order_date < DATE '2027-01-01'
```

Avoid:

```sql
WHERE EXTRACT(YEAR FROM order_date) = 2026
```

Reason:
- date ranges are usually more index-friendly
- date ranges are easier to translate across dialects
- date ranges are easier for guardrails to inspect

## Repair Guidance
When validation fails, repair prompts should include the active dialect and validation errors.

Examples:
- Unknown table: if the unknown name is a column from retrieved context, use the owning table in `FROM`.
- Unsupported date function: replace the function with a dialect-supported date range.
- Missing limit: add the dialect-specific row limit.
- Invalid column: use only columns present in retrieved schema chunks.

Do not repair by inventing tables, columns, or business metrics.

## Testing Checklist
- PostgreSQL prompt includes PostgreSQL date literal and limit guidance.
- H2 prompt avoids unsupported PostgreSQL-only functions.
- Year filter prompt prefers half-open date ranges.
- Guardrail accepts valid dialect SQL.
- Guardrail rejects unknown tables and columns.
- Repair prompt includes dialect and validation errors.
- Repair converts column-as-table mistakes into owning-table SQL.
- Repair converts avoidable date functions into date ranges when context supports it.
