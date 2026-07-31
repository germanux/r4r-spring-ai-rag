# Task 03 implementation guide

## Schema/config

- `CREATE EXTENSION IF NOT EXISTS vector;` is allowed.
- Create application table/indexes with plain `CREATE`, not `IF NOT EXISTS`, so
  Flyway detects drift.
- Schema must match Spring AI 1.0.0 and `vector(768)` with cosine indexing/search.
- Disable Spring AI automatic schema creation. Tests use the disposable PostgreSQL
  service and a deterministic 768-dimensional embedding model; no Ollama request.

## Store contract

`PgVectorKnowledgeStore` validates all public inputs. For indexing, construct and
validate every source replacement before the first mutation. Detect duplicate logical
identities globally before any `DELETE`. Stable IDs use an unambiguous encoding of
source, heading path and ordinal and are independent of content.

Each replacement deletes only rows whose metadata source matches, then adds the
prepared documents in one transaction. Preserve exact content plus metadata:
`source`, `headingPath`, `ordinal`. Retrieval rejects malformed metadata defensively.
Search rejects blank query, non-positive `topK`, non-finite/out-of-range threshold;
then delegates through Spring AI `SearchRequest`.

## Required evidence

- Flyway schema/table/index/dimension checks.
- Reindexing is idempotent; changed content keeps logical IDs.
- Replacement removes stale rows and preserves other sources.
- Duplicate identity in a later source fails before any source mutates; compare exact
  ordered before/after snapshots.
- Search preserves citation data, enforces `topK`, keeps a relevant result and excludes
  an irrelevant result at the strict threshold.
- Deterministic embedding model proves 768 length, repeatability and distinguishable
  controlled texts.
- Metadata tests use reachable malformed documents, not builders/constructors that
  fail before `fromDocument()`.
- Rollback proof uses a real PostgreSQL `BEFORE INSERT` trigger during
  `replaceSource(...)`; compare exact `id/content/metadata/embedding` snapshots.

No Mockito, `@SpyBean`, AOP, reflection, subclassing or production failure hook.
Gate: `./scripts/task-gate.sh task-03-pgvector`
