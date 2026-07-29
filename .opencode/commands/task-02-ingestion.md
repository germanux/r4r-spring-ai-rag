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
