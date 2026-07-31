# Task 03 focused implementation contract

Allowed product files are the Task 03 Java, SQL, YAML and tests only.

- Flyway creates plain `vector_store`, UUID PK, JSONB metadata, `vector(768)`, HNSW
  cosine index and source index. Spring AI schema initialization stays disabled.
- Validate every request and duplicate logical identity before the first mutation.
- Stable ID = length-prefixed source + ordered headings + fixed-width ordinal;
  content does not participate.
- `replaceSource(source, [])` deletes only that source. Replacement is transactional.
- Persist exact content plus `source`, `headingPath`, `ordinal`; defensively rebuild
  `MarkdownChunk` from retrieval metadata.
- Search validates query, positive `topK`, finite threshold in `[0,1]`, then uses
  Spring AI 1.0.0 `SearchRequest` and `VectorStore.similaritySearch`.
- Tests use a deterministic local 768-D embedding model and real pgvector. Prove
  schema/index, idempotency, stable IDs, stale-row deletion, other-source
  preservation, duplicate prevalidation, exact citations, topK, threshold inclusion
  and exclusion, malformed metadata and rollback with a real `BEFORE INSERT` trigger.

Repair order: compile, test-compile, focused failure, official gate. Do not rewrite a
whole class when a method-level correction is sufficient.

Gate: `./scripts/task-gate.sh task-03-pgvector`
