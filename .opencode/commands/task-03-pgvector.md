# Task 03 — Spring AI PgVector

## Outcome

- Flyway creates the application-owned `vector_store` schema.
- Spring AI `PgVectorStore` uses the existing datasource.
- Embeddings are exactly 768 dimensions and cosine similarity is consistent.
- Stable IDs depend on source, heading path and ordinal, not content.
- Index/replace is deterministic, source-scoped and transactional.
- Search preserves exact citation metadata and enforces `topK`/threshold.
- PostgreSQL integration tests use a deterministic local embedding model.

No handwritten Ollama client, Spring AI schema initialization, chat, REST or frontend.

Gate: `./scripts/task-gate.sh task-03-pgvector`
