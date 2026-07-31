# Task 03 implementation guide

## Schema/config

- Enable `vector`; use plain Flyway `CREATE` statements so drift is visible.
- Match Spring AI 1.0.0, cosine search and `vector(768)`.
- Tests use disposable PostgreSQL and deterministic 768-D embeddings, never Ollama.

## Store contract

Validate public inputs and prepare every source replacement before mutation. Detect
all duplicate logical identities before any `DELETE`. Stable IDs derive unambiguously
from source, heading path and ordinal, independent of content.

Replace one source transactionally: delete only that source, then add prepared
Documents preserving `source`, `headingPath` and `ordinal`. Reject malformed metadata.
Validate query, `topK` and finite threshold before delegating through `SearchRequest`.

## Evidence

Prove schema/index/dimension, idempotent reindex, stable IDs, stale-row deletion,
other-source preservation, pre-mutation duplicate failure, citation metadata, `topK`,
threshold behavior, deterministic embeddings and real PostgreSQL rollback via a
`BEFORE INSERT` trigger. Compare exact ordered snapshots for rollback assertions.

No mocks, spies, AOP, reflection, subclass failure hooks or weakened gates.
Gate: `./scripts/task-gate.sh task-03-pgvector`
