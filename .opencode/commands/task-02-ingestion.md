# Task 02 — Deterministic ingestion

## Objective

Implement the smallest deterministic ingestion slice on top of the green baseline.

## Required outcome

- Add Flyway migration `V2__knowledge_ingestion.sql`.
- Represent source documents and chunks with stable identities.
- Use a SHA-256 checksum over canonical source bytes.
- Persist source path, checksum, heading path, chunk ordinal and content.
- Re-ingesting unchanged content must not duplicate documents or chunks.
- Changed content must replace the previous document state atomically.
- Add focused unit tests and PostgreSQL integration test
  `KnowledgeIngestionServiceIT`.

## Restrictions

No embeddings, vector search, chat, REST or frontend in this task. Do not modify
`scripts/task-gate.sh` or task definitions.

## Gate

`./scripts/task-gate.sh task-02-ingestion`

## Mandatory implementation route

Before editing, read in full:

`.opencode/commands/task-02-ingestion-implementation-guide.md`

This guidance is normative. Do not reconsider alternative rollback strategies.

Acceptance blockers:

1. `KnowledgeIngestionServiceIT` MUST inject the Spring-managed
   `KnowledgeIngestionService` with `@Autowired`.

2. NEVER construct `KnowledgeIngestionService` manually in an integration test.

3. Remove every test-only system property, failure flag, and simulated-failure
   hook from production code and tests.

4. Force the mid-replacement failure using a temporary PostgreSQL
   `BEFORE INSERT` trigger on `knowledge_chunks`.

5. Snapshot and compare the exact source checksum and exact ordered chunk rows
   before and after the failed replacement.

6. Use the transaction-bound JDBC connection when creating PostgreSQL `TEXT[]`
   values.

7. Run `./scripts/task-gate.sh task-02-ingestion` without piping it through grep.

8. Stop after the official gate result. Do not perform Git writes.
