# MAG Implementation Design Note

## Purpose
Define how to add Memory-Augmented Generation to this NL-to-SQL agent without sending full conversation history to the LLM.

MAG means:

```text
retrieve relevant memory
+ retrieve relevant schema/rules
+ build working context
+ generate safely
```

## Target Flow
```text
1. Receive user input
2. Load session state
3. Retrieve relevant memory
4. Resolve follow-up references
5. Normalize and correct input
6. Retrieve schema/rules/join paths
7. Build working context
8. Generate SQL
9. Validate with guardrails
10. Execute and store new memory
```

## Techniques And Purpose

### 1. Tenant Scope
Purpose: prevent memory/data leakage across customers or organizations.

Every memory lookup should filter by:
- tenant id
- user id or role
- datasource id
- conversation id where needed

Example:

```sql
WHERE tenant_id = ?
  AND datasource_id = ?
```

### 2. Session State
Purpose: know the active operating context.

Stores:
- tenant id
- user id
- datasource id
- SQL dialect
- conversation id
- permissions

Use before retrieval so the agent searches only allowed memory and schema.

### 3. Recent Window
Purpose: resolve near follow-up references without searching all history.

Examples:
- last 10 turns
- last 5 result references
- last 30 minutes

Use for phrases like:

```text
above
that list
same filter
previous result
```

### 4. Result References
Purpose: support follow-up queries using prior results safely.

Store structured result references, not full sensitive rows.

Example:

```json
{
  "resultSetId": "r1",
  "entity": "customer",
  "keyColumn": "id",
  "keys": [1, 2],
  "summary": "2 customers returned"
}
```

Use case:

```text
show customers except the list above
-> exclude customer ids [1, 2]
```

### 5. Memory Retrieval
Purpose: find only relevant past context.

Use hybrid retrieval:
- exact filters for tenant/user/datasource/conversation
- recent window for short-term references
- pg_trgm for fuzzy labels/entities
- pgvector for semantic memory summaries

Do not send all memory to the LLM.

### 6. Context Resolution
Purpose: rewrite dependent user input into standalone intent.

Example:

```text
Original:
show customers except the list above

Resolved:
show customers excluding customer ids [1, 2]
```

If memory cannot be resolved safely, ask clarification.

### 7. Working Context
Purpose: final bounded context for one LLM call.

Contains:
- current question
- resolved memory references
- retrieved schema chunks
- retrieved business rules
- join paths
- policies
- dialect
- confidence

This is not full memory. It is selected, validated context.

### 8. Memory Write-Back
Purpose: make future follow-ups possible.

After execution, store:
- question
- generated SQL
- retrieved chunk ids
- result reference
- row keys when safe
- guardrail decision
- outcome

Do not store unnecessary sensitive row data.

## Suggested Tables
```text
agent_session
agent_conversation_turn
agent_memory
agent_result_reference
agent_memory_embedding
agent_audit_event
```

## Recommended Implementation Phases

### Phase 1: Session And Result References
- Add conversation id to requests/responses.
- Store result references after successful query execution.
- Resolve "above", "that list", and "previous result".

### Phase 2: Memory Retrieval
- Add memory repository.
- Add recent-window lookup.
- Add pg_trgm search over memory labels.
- Add pgvector search over memory summaries.

### Phase 3: Working Context
- Create typed `WorkingContext`.
- Include resolved memory, retrieval context, policies, and dialect.
- Pass `WorkingContext` to prompt builder.

### Phase 4: Governance And Audit
- Add tenant/user/datasource filters.
- Add memory TTL and retention rules.
- Add audit logs for memory read/write decisions.
- Add redaction rules for sensitive data.

## Enterprise Rule
MAG should not mean "send all chat history to the LLM".

Enterprise MAG means:

```text
governed memory store
-> scoped retrieval
-> selected relevant memory
-> typed working context
-> safe generation
```
