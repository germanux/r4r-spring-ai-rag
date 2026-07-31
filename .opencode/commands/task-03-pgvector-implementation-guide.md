# Task 03 — PgVector implementation and final recovery guide

## Status and authority

This file is the single normative implementation guide for
`task-03-pgvector`.

It supersedes earlier Task 03 companion or recovery guides when they conflict.
Do not modify task definitions, controller code, progress, memory, scripts, Git
state, Task 02 production code, or Task 04 code.

The goal is to finish the existing implementation, remove false-positive tests,
prove transactional behavior against real PostgreSQL/pgvector, run the exact task
gate, and stop.

---

## 1. Mandatory execution order

1. Read:
   - `AGENTS.md`;
   - `.opencode/commands/task.md`;
   - `.opencode/memory.md`;
   - `.opencode/commands/task-03-pgvector.md`;
   - this file;
   - the current Codex plan or correction packet.
2. Inspect only the Task 03 paths listed in section 2.
3. Translate every requirement in this guide into an exact code or test check.
4. Preserve working code that already satisfies the requirement.
5. Correct every remaining defect, not only the first compiler error.
6. Reopen every edited file before the gate.
7. Run exactly:

   ```bash
   ./scripts/task-gate.sh task-03-pgvector
   ```

8. Report:
   - changed paths;
   - exact gate exit;
   - exact unit and integration test totals;
   - first unproven condition, or `none`.
9. Stop. Do not perform Git writes.

Do not run `git add`, `git commit`, `git reset`, `git checkout`, `git clean`,
`git stash`, `git rebase`, `git merge`, `git push`, or any other Git write.

---

## 2. Allowed Task 03 paths

Production:

```text
src/main/java/com/riansares/r4r/vector/PgVectorKnowledgeStore.java
src/main/resources/application.yml
src/main/resources/db/migration/V3__pgvector_store.sql
```

Tests:

```text
src/test/java/com/riansares/r4r/vector/DeterministicEmbeddingModel.java
src/test/java/com/riansares/r4r/vector/PgVectorTestConfiguration.java
src/test/java/com/riansares/r4r/vector/PgVectorKnowledgeStoreIT.java
src/test/java/com/riansares/r4r/vector/PgVectorKnowledgeStoreTransactionalRollbackIT.java
src/test/resources/application-test.yml
```

Do not create REST controllers, WebFlux endpoints, Reactor services, chat logic,
frontend code, Playwright tests, Testcontainers, handwritten Ollama clients, a
second chunk model, or unrelated abstractions.

---

## 3. Stale-build preflight

The source tree may already contain the corrected `MarkdownChunk` API while
`target/classes` still contains an obsolete nested
`PgVectorKnowledgeStore.Chunk`.

If compiler output mentions either of these obsolete conditions:

```text
PgVectorKnowledgeStore.Chunk
cannot find method replaceSource(...)
```

first verify that the current production source already:

- imports `com.riansares.r4r.chunking.MarkdownChunk`;
- has no nested `Chunk` record;
- exposes `replaceSource(String, List<MarkdownChunk>)`.

When the source is correct, do not rewrite it to match stale bytecode. Remove only
generated Maven output:

```bash
rm -rf target
```

Then continue with the exact task gate.

Do not remove source files, migrations, runtime locks, or controller evidence.

---

## 4. Required production contract

`PgVectorKnowledgeStore` must remain a Spring-managed service using:

```java
VectorStore
JdbcTemplate
MarkdownChunk
```

Constructor dependencies must be null-checked.

### 4.1 Public methods

Required public API:

```java
void index(List<MarkdownChunk> chunks)

void replaceSource(
        String source,
        List<MarkdownChunk> chunks)

List<MarkdownChunk> search(
        String query,
        int topK,
        double minScore)
```

Both mutating methods must be annotated with:

```java
@Transactional
```

Do not reintroduce a nested `Chunk` record.

### 4.2 Validate the complete request before mutation

`index(...)` must validate the entire list and group all chunks before deleting any
database row.

`replaceSource(...)` must validate the source and every supplied chunk before
deleting any database row.

Required validation:

```text
chunks list        non-null
chunk entry        non-null
chunk source       non-null and non-blank
chunk content      non-null and non-blank
replacement source non-null and non-blank
every replacement chunk belongs to that source
query              non-null and non-blank
topK               greater than zero
minScore           finite and in [0.0, 1.0]
```

Use explicit `IllegalArgumentException` for invalid public input.

For a null chunk entry, use an explicit production check:

```java
private static void validateChunk(MarkdownChunk chunk) {
    if (chunk == null) {
        throw new IllegalArgumentException(
                "chunk must not be null");
    }

    requireNonBlank(chunk.source(), "chunk.source");
    requireNonBlank(chunk.content(), "chunk.content");
}
```

Do not rely on a later accidental `NullPointerException`.

`MarkdownChunk` itself already prevents a negative index and null record
components. Do not claim that `PgVectorKnowledgeStore` rejected those values when
the `MarkdownChunk` constructor failed first.

### 4.3 Spring AI 1.0.0 API

Use the actual API available on the project classpath.

Search:

```java
SearchRequest request = SearchRequest.builder()
        .query(query)
        .topK(topK)
        .similarityThreshold(minScore)
        .build();

List<Document> documents =
        vectorStore.similaritySearch(request);
```

Read text with:

```java
document.getText()
```

Build documents with:

```java
Document.builder()
        .id(id)
        .text(chunk.content())
        .metadata(metadata)
        .build();
```

Do not use:

```text
vectorStore.search(...).getElements()
document.getContent()
withId(...)
withContent(...)
withMetadata(...)
```

### 4.4 Exact original content and citation metadata

Persist exactly:

```text
Document.text        = MarkdownChunk.content
metadata.source      = MarkdownChunk.source
metadata.headingPath = MarkdownChunk.headingPath
metadata.ordinal     = MarkdownChunk.index
```

Do not prepend headings to `Document.text`.

A retrieved result must reconstruct:

```java
new MarkdownChunk(
        source,
        headingPath,
        ordinal,
        originalContent)
```

The integration test must prove exact round trip for:

```text
source
ordered heading path
ordinal
content
```

### 4.5 Defensive retrieval conversion

`fromDocument(...)` must reject corrupted retrieval data.

Required checks:

```text
document is non-null
document text is non-null
metadata exists
source is a non-blank String
headingPath is a Collection
every headingPath element is a String
ordinal is a Number
ordinal is integral
ordinal is non-negative
```

Malformed citation metadata must raise a clear `IllegalStateException`.
A null method argument may raise `IllegalArgumentException`.

Do not use an unchecked cast to `List<String>`.

### 4.6 Stable IDs

The logical identity is:

```text
source
ordered heading path with unambiguous boundaries
ordinal encoded as a fixed-width integer
```

Content must not participate in identity.

A length-prefixed UTF-8 representation is acceptable and already boundary-safe:

```text
length(source bytes) + source bytes
heading count
length(heading bytes) + heading bytes for each heading
fixed-width ordinal
```

Do not replace a correct length-prefixed implementation merely to use NUL
separators.

Hash the canonical bytes with SHA-256, take the first 16 bytes, and set valid UUID
version and variant bits.

Required behavior:

- identical logical chunk produces the same UUID;
- changing only content preserves the UUID;
- changing source changes the UUID;
- changing a heading boundary changes the UUID;
- changing ordinal changes the UUID;
- `["ab", "c"]` and `["a", "bc"]` never collide because of framing;
- duplicate logical identities in one replacement request fail before mutation.

### 4.7 Source replacement and batching

For each represented source:

1. construct and validate all `Document` objects;
2. reject duplicate logical IDs;
3. delete only rows with matching `metadata->>'source'`;
4. add the replacement documents in one batch;
5. preserve rows belonging to every other source.

Required batch call:

```java
vectorStore.add(List.copyOf(documents));
```

Do not call:

```java
vectorStore.add(List.of(document))
```

inside a loop.

An empty explicit replacement must remove every existing vector for that source.

---

## 5. Flyway and configuration

Flyway is the sole schema owner.

### 5.1 Application configuration

The PgVector configuration must be exactly consistent with the migration:

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        initialize-schema: false
        dimensions: 768
        distance-type: COSINE_DISTANCE
        index-type: HNSW
        schema-name: public
        table-name: vector_store
        schema-validation: true
```

Do not use:

```yaml
distance-type: cosine
```

Do not make dimensions independently configurable while the migration is fixed at
`VECTOR(768)`.

### 5.2 V3 migration

Required schema:

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding VECTOR(768) NOT NULL
);

CREATE INDEX idx_vector_store_embedding
    ON vector_store
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 100);

CREATE INDEX idx_vector_store_source
    ON vector_store ((metadata->>'source'));
```

Application-owned table and indexes should use plain `CREATE`, not
`IF NOT EXISTS`. Flyway must expose drift rather than silently accepting a
different pre-existing object.

Do not add `pgcrypto`; application code supplies UUIDs.

PostgreSQL assertions must verify:

```text
public.vector_store exists
id type is uuid
id is the primary key
metadata type is jsonb
embedding type is vector(768)
HNSW index exists
operator class is vector_cosine_ops
Flyway latest successful version is 3
```

Do not prove schema by searching SQL source text.

---

## 6. Deterministic test embedding model

`DeterministicEmbeddingModel` must:

- be test-scoped;
- implement the exact Spring AI 1.0.0 `EmbeddingModel` contract;
- return exactly 768 dimensions;
- return identical vectors for identical input;
- return predictably distinguishable vectors for different token sets;
- normalize non-empty vectors;
- make no network call;
- use `Document.getText()`.

`PgVectorTestConfiguration` must expose it as the primary test bean:

```java
@Bean
@Primary
EmbeddingModel deterministicEmbeddingModel() {
    return new DeterministicEmbeddingModel();
}
```

The integration tests must use:

```text
real Spring application context
real Spring-managed PgVectorStore
real PostgreSQL/pgvector
no live Ollama embedding request
```

Do not replace PgVector with an in-memory vector store.

---

## 7. Eliminate false-positive tests

A test is invalid when fixture construction throws before the system under test is
called.

### 7.1 Null chunk entry

Never write:

```java
assertThatThrownBy(() ->
        store.index(List.of(validChunk, null)))
```

`List.of(...)` rejects `null` before `store.index(...)`.

Use:

```java
List<MarkdownChunk> chunks = new ArrayList<>();
chunks.add(validChunk);
chunks.add(null);

assertThatThrownBy(() -> store.index(chunks))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("chunk must not be null");
```

The collection must be constructed before the assertion lambda. The lambda must
contain only the call to `store.index(...)`.

### 7.2 Invalid MarkdownChunk construction

Do not claim `PgVectorKnowledgeStore` rejected a negative index, null source, or
null content when `new MarkdownChunk(...)` rejected it first.

Either:

- test that invariant in the `MarkdownChunk` test suite; or
- remove the false store-level test.

Store-level tests may cover values that can actually reach the store, such as:

```text
null chunk entry
blank source
blank content
cross-source replacement
null list
blank query
invalid topK
NaN or infinite minScore
minScore outside [0.0, 1.0]
```

### 7.3 Invalid Document fixtures

Construct every `Document` fixture before `assertThatThrownBy(...)`.

Valid pattern:

```java
Document document = Document.builder()
        .id("test-id")
        .text("content")
        .metadata(malformedMetadata)
        .build();

assertThatThrownBy(() -> store.fromDocument(document))
        .isInstanceOf(IllegalStateException.class);
```

Do not put `Document.builder().build()` inside the assertion lambda when the
builder itself may reject the fixture.

If Spring AI prevents constructing a `Document` with null text or null metadata,
do not claim `fromDocument(...)` was tested for those unreachable states. Preserve
the defensive production checks, but mark the direct test as unavailable rather
than accepting a false positive.

### 7.4 General assertion rule

For every exception test:

1. build all fixtures before the assertion;
2. verify fixture construction succeeded;
3. put only the system-under-test invocation inside the lambda;
4. assert the exception type and a meaningful message;
5. remove or rewrite any test whose exception originates from the JDK collection
   factory, record constructor, Spring AI builder, Mockito setup, SQL fixture
   creation, or unrelated code.

---

## 8. Real transactional rollback proof

Remove Mockito and `@SpyBean` from
`PgVectorKnowledgeStoreTransactionalRollbackIT`.

Do not simulate failure by making a spy throw before the real PgVector insert.
Use PostgreSQL to fail during the real insertion.

### 8.1 Test structure

The test must autowire:

```java
PgVectorKnowledgeStore
JdbcTemplate
```

It must use the real Spring-managed `VectorStore` indirectly through the real
`PgVectorKnowledgeStore`.

Required sequence:

1. drop any stale failure trigger and function;
2. truncate `vector_store`;
3. seed original source state through `store.index(...)`;
4. capture exact ordered database rows;
5. install a temporary PostgreSQL `BEFORE INSERT` trigger;
6. call `store.replaceSource(...)` with replacement chunks;
7. assert the PostgreSQL-triggered failure;
8. drop the trigger and function in `finally`;
9. query exact state again;
10. assert the state is identical to the original snapshot.

### 8.2 Failure trigger

Use a temporary trigger equivalent to:

```sql
CREATE OR REPLACE FUNCTION fail_vector_store_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'forced vector store insert failure';
END;
$$;
```

```sql
CREATE TRIGGER fail_vector_store_insert_trigger
BEFORE INSERT ON vector_store
FOR EACH ROW
EXECUTE FUNCTION fail_vector_store_insert();
```

Cleanup must always run:

```sql
DROP TRIGGER IF EXISTS
    fail_vector_store_insert_trigger
ON vector_store;
```

```sql
DROP FUNCTION IF EXISTS
    fail_vector_store_insert();
```

Run cleanup before each test and in `finally`.

### 8.3 Exact snapshot

Capture every row for the tested source in deterministic order:

```text
id
content
metadata JSON text
embedding text
```

Order by:

```sql
(metadata->>'ordinal')::int, id
```

Use immutable test records, for example:

```java
private record VectorRowSnapshot(
        String id,
        String content,
        String metadata,
        String embedding) {
}
```

The final assertion must be:

```java
assertThat(after).isEqualTo(before);
```

A row-count-only assertion is insufficient.

This proves that deletion and the failed real PgVector insertion participate in
the same Spring transaction.

Do not use:

```text
@SpyBean
Mockito
doAnswer
simulated VectorStore exception
manual construction of PgVectorKnowledgeStore
raw seed INSERT with a hand-built 768-value vector
```

---

## 9. Required integration evidence

`PgVectorKnowledgeStoreIT` must prove all of the following against PostgreSQL:

1. Flyway V3 is applied.
2. `vector(768)` exists.
3. UUID primary key exists.
4. JSONB metadata exists.
5. HNSW index uses `vector_cosine_ops`.
6. deterministic embedding length is 768.
7. identical reindex preserves exact row count and IDs.
8. changed content keeps the logical ID and changes stored content.
9. reduced source replacement removes stale ordinals.
10. source A replacement preserves source B.
11. empty replacement removes only its source.
12. canonical field boundaries produce different IDs.
13. source, heading path, ordinal, and original content round-trip exactly.
14. `topK` is enforced.
15. a strict threshold retains the expected relevant result and excludes the
    irrelevant result.
16. no live Ollama call is required.
17. public input validation reaches the store and is not a fixture failure.
18. malformed reachable metadata is rejected defensively.
19. PostgreSQL-triggered mid-replacement failure restores exact prior state.

For threshold tests, do not accept an empty strict result as proof. Assert both:

```text
expected relevant source is present
known irrelevant source is absent
```

---

## 10. Focused source checklist

Before the gate, reopen all changed files and verify:

### `PgVectorKnowledgeStore.java`

- [ ] imports `MarkdownChunk`;
- [ ] no nested `Chunk`;
- [ ] `index(...)` validates all entries before mutation;
- [ ] `replaceSource(...)` validates all entries before mutation;
- [ ] null entries produce explicit `IllegalArgumentException`;
- [ ] `search(...)` validates query, `topK`, and finite score range;
- [ ] `similaritySearch(...)` is used;
- [ ] `Document.builder().id().text().metadata()` is used;
- [ ] `Document.getText()` is used;
- [ ] original content is persisted unchanged;
- [ ] source, heading path, and ordinal are metadata;
- [ ] IDs are content-independent and boundary-safe;
- [ ] duplicate IDs fail before deletion;
- [ ] deletion is source-scoped;
- [ ] insert is one batch;
- [ ] mutation methods are transactional;
- [ ] metadata conversion is defensive.

### `V3__pgvector_store.sql`

- [ ] Flyway owns the table;
- [ ] UUID primary key;
- [ ] JSONB metadata;
- [ ] `VECTOR(768)`;
- [ ] HNSW;
- [ ] `vector_cosine_ops`;
- [ ] source expression index;
- [ ] no unrelated extension;
- [ ] no drift-hiding `IF NOT EXISTS` on application-owned table/indexes.

### `application.yml`

- [ ] `initialize-schema: false`;
- [ ] `dimensions: 768`;
- [ ] `distance-type: COSINE_DISTANCE`;
- [ ] `index-type: HNSW`;
- [ ] `schema-name: public`;
- [ ] `table-name: vector_store`;
- [ ] `schema-validation: true`.

### Test files

- [ ] deterministic 768-dimensional test model;
- [ ] real PostgreSQL and real PgVectorStore;
- [ ] no Ollama network call;
- [ ] no in-memory vector store;
- [ ] no Mockito or `@SpyBean` rollback proof;
- [ ] rollback failure comes from a PostgreSQL trigger;
- [ ] exact rollback snapshot equality;
- [ ] null-entry test uses a mutable null-capable list;
- [ ] exception fixtures are constructed outside assertion lambdas;
- [ ] no false store-level claims for invariants rejected by constructors;
- [ ] strict similarity threshold proves inclusion and exclusion;
- [ ] exact citation round trip.

---

## 11. Gate and completion

Run only the authoritative task command:

```bash
./scripts/task-gate.sh task-03-pgvector
```

Do not pipe, redirect, wrap, truncate, or synthesize the result.

A successful generic compile is not sufficient.

Task 03 is ready for Codex review only when:

```text
gate exit = 0
all unit tests pass
all integration tests pass
real PostgreSQL/pgvector is exercised
rollback is proven without mocks
false-positive exception tests are removed
working tree remains uncommitted by OpenCode
```

Final report format:

```text
Task: task-03-pgvector
Changed paths:
- ...

Gate:
./scripts/task-gate.sh task-03-pgvector

Exit:
0

Tests:
- Surefire: <exact total>, failures 0, errors 0, skipped <exact>
- Failsafe: <exact total>, failures 0, errors 0, skipped <exact>

First unproven condition:
none
```

Do not mark progress, edit memory, commit, or push. The Python controller performs
those actions only after Codex returns `ACCEPT`.
