# Task Prompt: Governed Business Rule Storage

## Source
- Checklist: `document/note/07-enterprise-readiness-checklist.md`
- Section: `14. Enterprise Target Summary`
- Highest-priority next step: `Move business rules from YAML to governed DB tables.`

## Objective
Implement governed business-rule storage while keeping both YAML-backed and database-backed rule sources configurable, migration-ready, and testable on both H2 and PostgreSQL.

The enterprise target is not to remove YAML unconditionally. YAML should remain supported for local development, tests, demos, bootstrap data, and deployments where file-based governance is accepted. Database-backed rules should be the production-grade option when enterprise standards require ownership, approval, versioning, effective dates, auditability, tenant scoping, and datasource scoping.

Follow Clean Architecture principles: keep governance/admin use cases separate from the main retrieval flow. The main flow should read already-approved effective rules through a stable port. Governance should manage drafts, approval status, versions, owners, and audit metadata through separate use cases and adapters.

The main NL-to-SQL flow must operate in consume-only mode for governed and indexed context. It should not refresh schema, approve rules, rebuild chunks, generate embeddings, or mutate vector indexes inline while answering a user question. Refresh, sync, and rebuild operations belong to admin/governance workflows, startup preparation, scheduled/background jobs, or explicit operator-triggered APIs.

## Implementation Prompt
You are working in the `natural-language-database-agent` repository. Implement enterprise-ready business-rule storage for retrieval.

Current state:
- Production retrieval no longer depends on hardcoded Java business rules.
- Business rules are loaded from external YAML.
- Business-rule schema references are validated against the active schema, and stale references are excluded during chunk building.
- The checklist says governed storage is partially implemented because the current source is YAML bootstrap only.

Required outcome:
- Add a database-backed business-rule source with governed metadata.
- Keep YAML and database rule sources configurable through application properties.
- Add Flyway so database schema changes are migration-managed and ready for future production evolution.
- Support both H2 and PostgreSQL with clear configuration and validation commands.
- Support PostgreSQL through Docker Compose for local enterprise-style testing.
- Provide UI/API support for managing governed business rules without coupling admin logic into retrieval.
- Make the governed rule indexing path explicit: governed DB rule -> canonical text -> business-rule chunk -> embedding -> vector index.
- Preserve existing behavior for tests and local/demo mode unless the configured rule source changes.
- Ensure retrieval uses the same `BusinessRuleSource` contract regardless of whether rules come from YAML or the database.

## Functional Requirements
1. Configuration
   - Add a property that selects the active business-rule source, for example `retrieval.business-rules.source=yaml|database`.
   - Default to the current YAML behavior unless the repository already has a stronger production-profile convention.
   - Support a clear production override to use database-backed rules.
   - Do not remove the existing YAML configuration.

2. Governed database model
   - Add relational storage for business rules with fields for:
     - stable rule id
     - rule text/content
     - aliases
     - schema references
     - owner
     - version
     - approval status
     - effective start/end dates
     - datasource
     - tenant
     - active flag
     - created/updated timestamps
   - Prefer normalized tables or JSON columns based on the current project style and database support.
   - Keep H2/local compatibility for default tests and local development.
   - Add Flyway migrations for the governed business-rule tables.
   - Provide H2-compatible and PostgreSQL-compatible migrations when SQL dialect differences require it.
   - Move new production DDL away from ad hoc `schema.sql` usage. Existing scripts may remain during transition, but new governed rule DDL should be Flyway-managed.

3. Database platform support
   - Support H2 for fast local tests and simple demo mode.
   - Support PostgreSQL for production-like behavior.
   - Ensure the existing Docker Compose PostgreSQL service can be used for this feature, or update Docker Compose if new environment variables are required.
   - Document how to start PostgreSQL locally and run the app against it.
   - Ensure database-backed business rules work in PostgreSQL, not only H2.

4. Rule selection semantics
   - Retrieval should only use rules that are active, approved, currently effective, and applicable to the requested datasource/tenant when such context is available.
   - If tenant or datasource context is not yet available in the request model, design the repository/API so those filters can be added without changing the core contract later.
   - Exclude stale schema references using the existing validation behavior.

5. YAML compatibility
   - YAML remains a supported `BusinessRuleSource`.
   - YAML can be used as bootstrap/import data if that fits the current architecture, but runtime retrieval must respect the configured source.
   - Tests should verify that YAML mode still works.

6. Database source
   - Implement a database-backed `BusinessRuleSource`.
   - Add focused repository/data-access tests for active/approved/effective filtering.
   - Add retrieval-level tests proving that database-backed rules are included in retrieval chunks when configured.

7. UI/API support
   - Put business-rule governance, schema refresh, and retrieval index rebuild operations in the same admin area, but keep them as separate pages/tabs/workflows.
   - Add minimal admin API support for governed business rules:
     - list rules
     - create draft rule
     - update draft rule
     - approve/publish rule
     - deactivate rule
     - re-index selected rule
   - Add minimal admin API support for schema/index operations:
     - view active schema snapshot metadata
     - view schema fingerprint
     - refresh schema
     - revalidate business-rule schema refs
     - rebuild affected retrieval indexes
   - Add minimal admin API support for retrieval index operations:
     - view indexed chunks
     - view embedding metadata
     - rebuild all derived retrieval data
     - rebuild business-rule chunks/embeddings only
     - rebuild schema chunks/embeddings only
   - Add UI support only to the level needed to operate the feature safely:
     - view governed rules
     - edit draft metadata/content
     - approve or deactivate rules
     - show approval status, owner, version, effective dates, tenant, and datasource
      - view schema fingerprint and last schema refresh time
      - view retrieval index status, content hash, embedding model, and indexed time
      - trigger refresh/rebuild actions from the appropriate page/tab
   - Keep admin/API DTOs separate from retrieval DTOs.
   - Do not allow the retrieval path to mutate governance state.

8. Clean Architecture organization
   - Keep domain objects independent from Spring, JDBC, web, and persistence annotations where practical.
   - Define ports/use cases around business intent, for example:
     - `BusinessRuleSource` or equivalent read port for retrieval
     - `BusinessRuleGovernanceService` or equivalent use case for admin operations
     - repository port for governed rule persistence
   - Keep adapters separated by responsibility:
     - YAML source adapter
     - JDBC/PostgreSQL/H2 persistence adapter
     - REST controller adapter
     - UI controller/static asset adapter if needed
   - The main NL-to-SQL flow should depend only on the read port and should not know how approvals or drafts are managed.
   - The main NL-to-SQL flow should consume the latest ready retrieval index and must not perform governance mutations, schema refreshes, or embedding rebuilds inline.
   - If the main flow detects missing, stale, or low-confidence context, it should return a safe clarification/error state or surface diagnostics instead of mutating indexes during the request.
   - Governance flow and main retrieval flow should be cleanly organized as two separate application concerns that share domain models/contracts where appropriate.

9. Observability and audit readiness
   - Ensure rule ids are stable and available in retrieved chunks or metadata so future audit fields can record selected rule ids.
   - Log or expose enough context to distinguish YAML-sourced and database-sourced rules during debugging.

10. Business-rule indexing flow
   - After governed rules are created, updated, approved, deactivated, or become effective/expired, the system must support indexing or re-indexing them for retrieval.
   - Indexing flow:

```text
governed business-rule database
-> select approved, active, currently effective rules
-> build stable canonical text
-> build business-rule chunks
-> generate embeddings
-> write/update vector index
-> retrieve by lexical and vector search
```

   - Governance tables remain the source of truth.
   - Vector storage is only a retrieval index, not the authoritative rule store.
   - H2/default mode may use the in-memory vector repository.
   - PostgreSQL mode should use `pgvector`.
   - Canonical text should include enough stable information to represent rule meaning, aliases, schema refs, tenant/datasource scope, and version.
   - Rule id, version, content hash, embedding model, datasource, tenant, and schema fingerprint should be stored with indexed chunks or vector metadata when supported.
   - Rule updates, approval changes, effective-date changes, canonical text changes, embedding model changes, or schema fingerprint changes should trigger or require re-indexing.
   - Admin/API support should include a way to trigger rebuild or re-index for governed business rules.

11. Rebuild and synchronization
   - Add a clear synchronization model for schema updates and business-rule changes.
   - Use a shared retrieval-index rebuild coordinator/use case for derived retrieval data, for example `RetrievalIndexRebuildService`.
   - Schema updates and business-rule governance changes should both flow into this rebuild coordinator instead of each feature rebuilding indexes independently.
   - The coordinator should treat schema metadata and governed business rules as source inputs, and chunks/vocabulary/embeddings/vector records as derived outputs.
   - The rebuild coordinator must not run as an implicit side effect of the user-facing NL-to-SQL query path.
   - The user-facing query path should read only committed, ready, active index records. It may check freshness metadata for confidence/diagnostics, but it should not repair stale indexes inline.
   - Store enough index metadata to detect stale derived data:
     - rule id
     - rule version
     - rule content hash
     - schema fingerprint
     - embedding model
     - tenant id
     - datasource id
     - indexed timestamp
   - A business-rule vector/index record is stale when:

```text
current rule content hash != indexed rule content hash
OR current schema fingerprint != indexed schema fingerprint
OR current embedding model != indexed embedding model
OR rule is no longer approved, active, or effective
```

   - Schema update flow:

```text
admin refreshes schema or datasource metadata changes
-> compute new schema fingerprint
-> rebuild schema chunks
-> revalidate approved business-rule schema refs
-> exclude or mark stale rules/chunks when refs are invalid
-> rebuild affected canonical text if schema-dependent text changes
-> rebuild affected embeddings/vector records
```

   - Business-rule change flow:

```text
admin creates, updates, approves, deactivates, or changes effective dates
-> recompute canonical text
-> update rule content hash
-> rebuild or remove affected business-rule chunks
-> rebuild or remove affected embeddings/vector records
```

   - Provide manual rebuild actions for operators:
     - rebuild selected rule
     - rebuild all business rules
     - refresh schema and rebuild affected indexes
     - rebuild all retrieval indexes
   - Automatic rebuild after rule approval/update is preferred when safe; otherwise the UI/API must clearly show pending/stale index state and provide a manual rebuild action.
   - Log rebuild reason, trigger source, affected rule ids, affected chunk ids, schema fingerprint, embedding model, and outcome.
   - Derived retrieval data must be safe to delete and rebuild from the governed rule tables plus current schema metadata.

12. Main-flow consume-only contract
   - The main flow is responsible for answering user questions using already-prepared trusted context.
   - Allowed main-flow operations:
     - read active schema snapshot metadata
     - read approved/effective business-rule chunks
     - read vocabulary candidates
     - read vector-search results
     - evaluate confidence and freshness metadata
     - continue, retry retrieval, fallback, or ask clarification
   - Disallowed main-flow operations:
     - create, update, approve, or deactivate business rules
     - refresh datasource schema
     - rebuild schema chunks
     - rebuild business-rule chunks
     - generate or persist embeddings
     - mutate `pgvector` or in-memory vector indexes
   - If required context is stale or unavailable, the main flow should fail safely with a controlled response and traceable diagnostics.

13. Admin UI organization
   - Use one admin module/area with separate pages or tabs:

```text
Admin
-> Business Rules
-> Schema Index / Refresh
-> Retrieval Index / Rebuild
-> Retrieval Diagnostics
```

   - `Business Rules` should manage governed rule content and lifecycle.
   - `Schema Index / Refresh` should manage schema snapshot visibility, refresh, fingerprint comparison, and schema-ref revalidation.
   - `Retrieval Index / Rebuild` should manage derived chunks, embeddings, vector records, stale status, and rebuild actions.
   - `Retrieval Diagnostics` should help explain selected chunks, vector scores, confidence, and failure reasons.
   - Keep UI navigation centralized, but keep application use cases separated:

```text
Business Rules tab -> BusinessRuleGovernanceService
Schema tab         -> SchemaIndexService
Rebuild tab        -> RetrievalIndexRebuildService
Diagnostics tab    -> RetrievalDiagnosticsService
```

## Non-Goals
- Do not build a large, polished admin console beyond the minimal UI/API required to operate governed rules.
- Do not implement complex multi-party approval workflow, comments, notifications, or maker/checker policies beyond basic approve/deactivate status transitions.
- Do not remove YAML support.
- Do not implement contradiction detection between business rules.
- Do not mix governance mutation logic into the NL-to-SQL query execution path.

## Acceptance Criteria
- The application can run with YAML-backed business rules using the existing/default configuration.
- The application can run with database-backed business rules by changing configuration.
- Flyway is configured and owns the new governed business-rule table migrations.
- H2 tests pass with the default test/local configuration.
- PostgreSQL can be started with Docker Compose and used to run the database-backed rule source.
- Database-backed retrieval only considers rules that are approved, active, and effective for the relevant tenant/datasource scope.
- Database-backed rules can be transformed into canonical text, business-rule chunks, embeddings, and vector index records.
- H2/default mode can retrieve indexed business-rule chunks using the in-memory vector repository.
- PostgreSQL mode can retrieve indexed business-rule chunks using `pgvector`.
- Governance tables remain the source of truth and vector/index tables are treated as rebuildable derived data.
- Schema changes trigger schema fingerprint comparison, business-rule schema-ref revalidation, and affected index rebuild.
- Business-rule changes trigger canonical text/content hash updates and affected chunk/vector rebuild or removal.
- The main NL-to-SQL flow is consume-only: it reads ready indexes/context and does not refresh schema, rebuild chunks, generate embeddings, or mutate vector indexes inline.
- Stale or unavailable retrieval context in the main flow results in safe fallback, clarification, or diagnostics rather than inline rebuild.
- Admin UI/API provides separate workflows for Business Rules, Schema Index / Refresh, Retrieval Index / Rebuild, and Retrieval Diagnostics inside one admin area.
- Rebuild logs include reason, trigger source, affected ids, schema fingerprint, embedding model, and outcome.
- Existing business-rule schema-reference validation still applies.
- Tests cover YAML compatibility, database rule filtering, and retrieval integration for database-backed rules.
- Tests or documented commands cover both H2 and PostgreSQL modes.
- Minimal API/UI support exists for governing rules without coupling governance operations to retrieval.
- Documentation or configuration examples clearly show how to switch between YAML and database modes.

## Suggested Files To Inspect First
- `src/main/java/com/nlda/retrieval/contract/BusinessRuleSource.java`
- `src/main/java/com/nlda/retrieval/config/BusinessRuleProperties.java`
- `src/main/java/com/nlda/retrieval/impl`
- `src/main/resources/application.yml`
- `src/main/resources/application-postgres-provider.yml`
- `docker-compose.yml`
- `src/test/java/com/nlda/retrieval/ConfigBusinessRuleSourceTest.java`
- `document/note/07-enterprise-readiness-checklist.md`

## Validation Commands
Run the existing test suite after implementation:

```powershell
.\mvnw.cmd test
```

Run PostgreSQL locally for production-like verification:

```powershell
docker compose up -d postgres
```

Run the app or tests against PostgreSQL using the project profile/configuration, for example:

```powershell
.\mvnw.cmd test -Dspring.profiles.active=postgres
```

If PostgreSQL-specific tests require Testcontainers, a dedicated Maven profile, or a different command, add the exact command to the project documentation.
