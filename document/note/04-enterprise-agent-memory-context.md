# Enterprise Agent Memory And Context Note

## Purpose
Explain the real memory/context techniques used by enterprise agents, including coding agents, copilots, and NL-to-SQL agents.

## Key Idea
Enterprise agents do not rely only on the current prompt. They build a controlled working context from multiple memory layers before each model call.

```text
user input
-> session state
-> conversation history
-> result references
-> retrieved knowledge
-> policy and tool context
-> working context for the LLM
```

## Important Techniques
- **Session state**: current user, tenant, datasource, active workflow, permissions, and selected context.
- **Conversation memory**: recent turns plus compact summaries of older turns.
- **Result references**: structured references to prior outputs, for example entity type and primary keys.
- **Semantic memory**: searchable knowledge such as business rules, documentation, schema meaning, or codebase facts.
- **Tool memory**: results from previous tool calls, files read, commands run, and artifacts created.
- **Context resolution**: converts follow-up phrases like "that", "above", or "same filter" into explicit intent.
- **Working memory**: the final bounded context sent to the LLM for one decision or generation step.

## Enterprise Rule
The LLM should not guess missing context. The agent should resolve context from governed memory, retrieve what is relevant, and ask clarification when the reference cannot be resolved safely.

For NL-to-SQL, this means:
- store prior result references, not just text
- retrieve schema and business rules dynamically
- resolve follow-up questions before SQL generation
- validate generated SQL against live metadata and policy

## Most Important For This Agent
Priority memory/context pieces for this NL-to-SQL agent:

1. **Session state**
   - active datasource
   - tenant/user permissions
   - SQL dialect
   - current conversation id

2. **Result references**
   - previous result set id
   - entity type, for example `customer`
   - primary key column
   - selected row ids

3. **Context resolution**
   - resolve "above", "that list", "same filter", "previous result"
   - rewrite follow-up questions into explicit standalone intent

4. **Semantic memory**
   - approved business rules
   - metric definitions
   - aliases and domain terms
   - preferred join paths

5. **Working memory**
   - current question
   - resolved references
   - retrieved schema chunks
   - retrieved business rules
   - guardrail constraints

This is why memory/context design is a core part of enterprise agent architecture, not a minor chat feature.
