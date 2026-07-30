# Task 03 — PgVector focused recovery guide

## Purpose

Complete `task-03-pgvector` against the actual Spring AI `1.0.0` API used by this
repository. Do not infer API names from memory. Treat compiler errors, the local
dependency contracts, PostgreSQL integration tests and the task gate as the
authoritative evidence.

Do not modify Task 02 production code, controller files, task definitions, progress,
memory, scripts or Git state while implementing this task.

## 1. Reconcile the Spring AI API before adding more design

The current `PgVectorKnowledgeStore` contains API calls that do not match the
Spring AI version on the project classpath.

Mandatory corrections:

1. Replace the unresolved vector search call with the API available in this
   project. For Spring AI 1.0.x, the expected shape is:

   ```java
   List<Document> documents =
           vectorStore.similaritySearch(searchRequest);
   ```

   Do not call `vectorStore.search(...).getElements()`.

2. Use the actual Spring AI `Document` text API:

   ```java
   String text = document.getText();
   ```

   Do not call `document.getContent()`.

3. Build documents with the actual builder names:

   ```java
   Document.builder()
           .id(stableId)
           .text(text)
           .metadata(metadata)
           .build();
   ```

   Do not use `withId`, `withContent` or `withMetadata`.

4. Compile immediately after this API reconciliation. Do not continue building
   tests around code that does not compile.

## 2. Reuse the existing deterministic chunk model

Do not create a second nested `Chunk` record inside `PgVectorKnowledgeStore`.

Use:

```java
com.riansares.r4r.chunking.MarkdownChunk
```

as the indexing input. It already carries:

- source;
- ordered heading path;
- deterministic chunk index;
- original chunk content.

For search results, either return `MarkdownChunk` directly or introduce one clearly
named retrieval result type only when an additional score is genuinely required.
Do not maintain two almost identical chunk models.

## 3. Preserve exact original chunk content

The text returned from vector search must remain suitable for RAG context and
citations.

Do not prepend headings to the persisted `Document` text and then return that
modified text as though it were the original chunk.

Preferred minimal representation:

```text
Document.text       = original MarkdownChunk.content
metadata.source     = MarkdownChunk.source
metadata.headingPath= MarkdownChunk.headingPath
metadata.ordinal    = MarkdownChunk.index
```

The heading path is citation metadata. If headings are deliberately included in the
embedding text, preserve the original content separately and prove that retrieval
reconstructs it exactly.

Add an exact round-trip assertion:

```text
indexed chunk source/content/ordinal/headingPath
==
retrieved chunk source/content/ordinal/headingPath
```

## 4. Canonical stable IDs

Stable IDs must be deterministic and unambiguous.

Do not hash raw string concatenation without boundaries. The following pairs must
not collapse to the same byte stream:

```text
["ab", "c"]
["a", "bc"]
```

Build a canonical identity from:

```text
source + NUL
each heading + NUL
ordinal encoded as fixed-width integer
```

Then derive the UUID deterministically from that canonical byte sequence.

Required tests:

- same logical chunk produces the same ID across repeated indexing;
- different source, heading boundary or ordinal produces a different ID;
- IDs remain stable when only the chunk content changes, so the same logical slot
  is replaced rather than duplicated.

## 5. Idempotent source replacement and stale-vector deletion

Stable IDs alone do not complete idempotence.

This scenario must pass:

```text
first indexing for source A: ordinals 0,1,2,3,4
second indexing for source A: ordinals 0,1,2
final database state: exactly 0,1,2
```

Before adding the current set for one source, delete the existing vectors for that
source using the supported Spring AI metadata-filter delete API or a narrowly
scoped PostgreSQL operation.

Then add the new documents as one batch:

```java
vectorStore.add(documents);
```

Do not call `add(List.of(document))` once per chunk.

Preserve vectors belonging to other sources.

Required PostgreSQL assertions:

- repeated identical indexing does not increase row count;
- changed content updates the existing logical chunks;
- reduced chunk count removes stale rows;
- replacing source A does not delete source B;
- final IDs and metadata exactly match the expected current set.

If delete-plus-add is expected to be atomic, prove it with a real PostgreSQL
transaction and a forced mid-operation failure. Do not claim atomicity based only
on method structure.

## 6. Metadata conversion must be defensive

PostgreSQL JSON metadata may be deserialized into generic collection and number
types.

Do not rely on an unchecked cast directly to `List<String>`.

Normalize and validate:

- `source` exists and is a non-blank string;
- `headingPath` is a collection whose elements are converted to strings;
- `ordinal` is a `Number` and non-negative;
- text is non-null;
- missing or malformed metadata raises a clear `IllegalStateException`.

Do not silently return corrupted citation data.

## 7. Validate public inputs

Before calling the vector store:

```text
query must be non-null and non-blank
topK must be > 0
minScore must be between 0.0 and 1.0 inclusive
chunks list must be non-null
chunk entries must be non-null
source and content must be non-null
ordinal/index must be non-negative
```

Add focused unit tests for invalid boundaries.

## 8. Flyway V3 must match the configured PgVectorStore

Flyway remains the sole schema owner. Keep:

```yaml
initialize-schema: false
```

The migration and Spring configuration must agree exactly on:

```text
schema: public
table: vector_store
embedding dimensions: 768
index: HNSW
distance/operator class: cosine / vector_cosine_ops
```

Use the Spring AI configuration value expected by the actual enum:

```yaml
distance-type: COSINE_DISTANCE
index-type: HNSW
dimensions: 768
schema-name: public
table-name: vector_store
initialize-schema: false
schema-validation: true
```

Do not leave the migration fixed at `vector(768)` while allowing an unrelated
runtime dimension value.

The application supplies UUID IDs, so do not add unrelated PostgreSQL extensions
unless the implemented SQL actually requires them. Keep the migration minimal.

Verify through PostgreSQL, not source-text matching:

- column type is `vector(768)`;
- primary key type is UUID;
- metadata column exists;
- HNSW index exists;
- operator class is `vector_cosine_ops`;
- Flyway reports schema version 3.

## 9. Deterministic integration testing without Ollama

Create a test-scoped deterministic `EmbeddingModel` with exactly 768 dimensions.

Requirements:

- no live Ollama calls;
- same input gives the same vector;
- different test inputs produce predictably distinguishable vectors;
- vector length is exactly 768;
- the real Spring-managed `VectorStore` and real PostgreSQL/pgvector are used.

Create or complete:

```text
src/test/java/com/riansares/r4r/vector/PgVectorKnowledgeStoreIT.java
```

The integration test must cover:

1. V3 schema and HNSW cosine index;
2. deterministic IDs;
3. repeated indexing without duplicates;
4. changed-content replacement;
5. stale-vector removal;
6. source isolation;
7. similarity search with `topK`;
8. similarity threshold behavior;
9. exact citation metadata round trip;
10. no dependency on a running Ollama embedding model.

## 10. Acceptance checklist before the official gate

Re-open every changed file and verify each statement explicitly:

- [ ] The project compiles against Spring AI 1.0.0.
- [ ] `similaritySearch(SearchRequest)` is used.
- [ ] `Document.getText()` is used.
- [ ] `Document.builder().id().text().metadata()` is used.
- [ ] The existing `MarkdownChunk` model is reused.
- [ ] Original chunk content survives retrieval exactly.
- [ ] Stable IDs use a canonical, boundary-safe representation.
- [ ] Documents are added in a batch.
- [ ] Reindexing removes stale vectors for the same source only.
- [ ] Metadata conversion is validated and defensive.
- [ ] Input boundaries are tested.
- [ ] V3, YAML and embedding dimensions all agree on 768.
- [ ] HNSW uses `vector_cosine_ops`.
- [ ] Integration tests use real PostgreSQL and a deterministic 768-dimensional
      test embedding model.
- [ ] No Ollama network call is required by the task gate.

Run exactly:

```bash
./scripts/task-gate.sh task-03-pgvector
```

Do not pipe, redirect or wrap the gate. Do not perform Git writes. Report the exact
test totals, changed paths and the first unproven checklist item. A green generic
build is not sufficient if any checklist item remains unproven.
