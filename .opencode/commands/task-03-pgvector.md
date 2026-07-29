# Task 03 — Spring AI PgVector

## Objective

Persist and retrieve deterministic chunks through Spring AI's PgVector integration.

## Required outcome

- Add Flyway migration `V3__pgvector_store.sql` owned by the application.
- Use the existing datasource and Spring AI PgVector abstractions.
- Use the configured Ollama embedding model and exactly 768 dimensions.
- Use cosine similarity consistently for indexing and querying.
- Preserve source and chunk metadata needed for citations.
- Add PostgreSQL integration test `PgVectorKnowledgeStoreIT`.

## Restrictions

Do not create a handwritten Ollama client. Do not let Spring AI initialize the
schema automatically. No chat, REST or frontend in this task.

## Gate

`./scripts/task-gate.sh task-03-pgvector`
