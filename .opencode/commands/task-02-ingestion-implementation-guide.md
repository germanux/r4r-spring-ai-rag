# Task 02 — Deterministic ingestion completion guide

## Purpose

Finish `task-02-ingestion` without design loops, Mockito/AOP workarounds, or manual construction of the transactional service.

This guide is normative for the current task. Implement the smallest coherent change set, run the official task gate, and stop.

---

## 1. Execution discipline

1. Read this document once.
2. Inspect only the current ingestion service, migration, and ingestion tests.
3. Do not reconsider the failure-injection strategy.
4. Do not use `@SpyBean`, Mockito, AOP, reflection, subclassing, or a production-only test hook.
5. Do not construct `KnowledgeIngestionService` manually in integration tests.
6. Do not perform Git writes.
7. Run only:

```bash
./scripts/task-gate.sh task-02-ingestion
```

8. Stop after reporting the exact gate evidence.

The rollback proof must use a temporary PostgreSQL trigger that raises an exception on chunk insertion.

---

## 2. Runtime preflight after an interrupted run

Pressing `Ctrl+C` can stop the visible parent while leaving an OpenCode, Node, Python, or model-client child process alive. If such a client survives, restarting Ollama can immediately reload the model because the client reconnects.

Before a new run, inspect processes:

```bash
ps -eo pid,ppid,etimes,cmd   | grep -E '[r]4r_codex_agent|[r]un-codex-agent|[o]pencode|[o]llama|[l]lama-server'
```

Stop only the stale agent/client PIDs first:

```bash
kill -INT <PID_1> <PID_2>
sleep 5
```

Escalate only if they remain:

```bash
kill -TERM <PID_1> <PID_2>
sleep 3
```

Then restart Ollama, if needed:

```bash
sudo systemctl restart ollama
```

Confirm that no stale client immediately reconnects:

```bash
ps -eo pid,ppid,etimes,cmd   | grep -E '[r]4r_codex_agent|[r]un-codex-agent|[o]pencode|[l]lama-server'
```

Do not kill unrelated IDE, Maven, PostgreSQL, or user-session processes.

---

## 3. Required production corrections

### 3.1 Inject existing Spring beans

`KnowledgeIngestionService` must receive the loader and chunker as constructor dependencies. Do not instantiate them inside the service.

Target constructor:

```java
public KnowledgeIngestionService(
        JdbcTemplate jdbcTemplate,
        MarkdownDocumentLoader documentLoader,
        HeadingMarkdownChunker chunker) {

    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    this.documentLoader = Objects.requireNonNull(documentLoader, "documentLoader");
    this.chunker = Objects.requireNonNull(chunker, "chunker");
}
```

The existing configuration already exposes `MarkdownDocumentLoader` and `HeadingMarkdownChunker` as Spring beans.

### 3.2 Catch only the expected empty-result condition

Do not convert every database exception into “source not found”.

Use:

```java
private Long findSourceIdByPathAndSha256(String sourcePath, byte[] sha256) {
    String sql = """
            SELECT id
            FROM knowledge_sources
            WHERE source_path = ?
              AND content_sha256 = ?
            """;

    try {
        return jdbcTemplate.queryForObject(sql, Long.class, sourcePath, sha256);
    } catch (EmptyResultDataAccessException exception) {
        return null;
    }
}
```

Any other database failure must propagate and fail the transaction.

### 3.3 Use the transaction-bound connection for `TEXT[]`

Do not call:

```java
jdbcTemplate.getDataSource().getConnection()
```

Do not return a `java.sql.Array` created from a connection that has already been closed.

Replace the chunks using `JdbcTemplate.execute(ConnectionCallback)` so Spring supplies the transaction-bound connection:

```java
private void replaceChunks(long sourceId, List<MarkdownChunk> chunks) {
    jdbcTemplate.update(
            "DELETE FROM knowledge_chunks WHERE source_id = ?",
            sourceId);

    String insertSql = """
            INSERT INTO knowledge_chunks (
                source_id,
                heading_path,
                ordinal,
                content_sha256,
                content
            )
            VALUES (?, ?, ?, ?, ?)
            """;

    jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            for (int ordinal = 0; ordinal < chunks.size(); ordinal++) {
                MarkdownChunk chunk = chunks.get(ordinal);
                Array headingPath = connection.createArrayOf(
                        "text",
                        chunk.headingPath().toArray(String[]::new));

                try {
                    statement.clearParameters();
                    statement.setLong(1, sourceId);
                    statement.setArray(2, headingPath);
                    statement.setInt(3, ordinal);
                    statement.setBytes(
                            4,
                            sha256(chunk.content().getBytes(StandardCharsets.UTF_8)));
                    statement.setString(5, chunk.content());
                    statement.executeUpdate();
                } finally {
                    headingPath.free();
                }
            }
        }
        return null;
    });
}
```

Remove the unused `documentSha256` parameter from the chunk-replacement method.

### 3.4 Keep checksum logic testable

Make the production checksum method package-private and static:

```java
static byte[] sha256(byte[] data) {
    try {
        return MessageDigest.getInstance("SHA-256").digest(data);
    } catch (NoSuchAlgorithmException exception) {
        throw new IllegalStateException(
                "SHA-256 algorithm not available",
                exception);
    }
}
```

The unit test must invoke this production method rather than duplicating `MessageDigest` logic inside the test.

---

## 4. Target service structure

Use the following structure as the implementation target. Adapt package imports to the current project; do not redesign unrelated classes.

```java
package com.riansares.r4r.ingestion;

import com.riansares.r4r.chunking.HeadingMarkdownChunker;
import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.document.KnowledgeDocument;
import com.riansares.r4r.document.MarkdownDocumentLoader;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;

@Service
public class KnowledgeIngestionService {

    private final JdbcTemplate jdbcTemplate;
    private final MarkdownDocumentLoader documentLoader;
    private final HeadingMarkdownChunker chunker;

    public KnowledgeIngestionService(
            JdbcTemplate jdbcTemplate,
            MarkdownDocumentLoader documentLoader,
            HeadingMarkdownChunker chunker) {

        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.documentLoader = Objects.requireNonNull(documentLoader, "documentLoader");
        this.chunker = Objects.requireNonNull(chunker, "chunker");
    }

    @Transactional
    public void ingest() {
        try {
            for (KnowledgeDocument document : documentLoader.loadAll()) {
                byte[] documentChecksum = sha256(
                        document.content().getBytes(StandardCharsets.UTF_8));

                Long unchangedSourceId = findSourceIdByPathAndSha256(
                        document.source(),
                        documentChecksum);

                if (unchangedSourceId != null) {
                    continue;
                }

                long sourceId = insertOrUpdateSource(
                        document.source(),
                        documentChecksum);

                List<MarkdownChunk> chunks = chunker.chunk(document);
                replaceChunks(sourceId, chunks);
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to ingest knowledge documents",
                    exception);
        }
    }

    private Long findSourceIdByPathAndSha256(
            String sourcePath,
            byte[] checksum) {

        String sql = """
                SELECT id
                FROM knowledge_sources
                WHERE source_path = ?
                  AND content_sha256 = ?
                """;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    Long.class,
                    sourcePath,
                    checksum);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private long insertOrUpdateSource(
            String sourcePath,
            byte[] checksum) {

        String sql = """
                INSERT INTO knowledge_sources (
                    source_path,
                    content_sha256
                )
                VALUES (?, ?)
                ON CONFLICT (source_path)
                DO UPDATE SET
                    content_sha256 = EXCLUDED.content_sha256,
                    updated_at = now()
                RETURNING id
                """;

        Long sourceId = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                sourcePath,
                checksum);

        if (sourceId == null) {
            throw new IllegalStateException(
                    "Database did not return a source id for " + sourcePath);
        }

        return sourceId;
    }

    private void replaceChunks(
            long sourceId,
            List<MarkdownChunk> chunks) {

        jdbcTemplate.update(
                "DELETE FROM knowledge_chunks WHERE source_id = ?",
                sourceId);

        String insertSql = """
                INSERT INTO knowledge_chunks (
                    source_id,
                    heading_path,
                    ordinal,
                    content_sha256,
                    content
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(insertSql)) {

                for (int ordinal = 0; ordinal < chunks.size(); ordinal++) {
                    MarkdownChunk chunk = chunks.get(ordinal);
                    Array headingPath = connection.createArrayOf(
                            "text",
                            chunk.headingPath().toArray(String[]::new));

                    try {
                        statement.clearParameters();
                        statement.setLong(1, sourceId);
                        statement.setArray(2, headingPath);
                        statement.setInt(3, ordinal);
                        statement.setBytes(
                                4,
                                sha256(chunk.content().getBytes(
                                        StandardCharsets.UTF_8)));
                        statement.setString(5, chunk.content());
                        statement.executeUpdate();
                    } finally {
                        headingPath.free();
                    }
                }
            }
            return null;
        });
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    exception);
        }
    }
}
```

---

## 5. Integration-test configuration

The integration test must call the Spring proxy.

Use:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class KnowledgeIngestionServiceIT {

    private static final Path KNOWLEDGE_ROOT = createKnowledgeRoot();

    @Autowired
    private KnowledgeIngestionService ingestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureKnowledgeRoot(
            DynamicPropertyRegistry registry) {

        registry.add(
                "r4r.knowledge.root",
                () -> KNOWLEDGE_ROOT.toString());

        registry.add(
                "r4r.knowledge.max-file-bytes",
                () -> 1_048_576);

        registry.add(
                "r4r.knowledge.max-chunk-chars",
                () -> 80);
    }

    private static Path createKnowledgeRoot() {
        try {
            return Files.createTempDirectory(
                    "r4r-knowledge-ingestion-it-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
```

Do not use `new KnowledgeIngestionService(...)` anywhere in this integration test.

Do not annotate the test class with `@Transactional`; the service transaction must commit or roll back independently so the assertions can inspect the final database state.

---

## 6. Test isolation

Before every integration test:

1. Remove any trigger/function left by an interrupted test.
2. Truncate both ingestion tables.
3. Remove all Markdown files under the temporary root.
4. Recreate the root directory.

Example:

```java
@BeforeEach
void resetState() throws IOException {
    dropFailureTrigger();

    jdbcTemplate.execute("""
            TRUNCATE TABLE
                knowledge_chunks,
                knowledge_sources
            RESTART IDENTITY CASCADE
            """);

    try (Stream<Path> paths = Files.walk(KNOWLEDGE_ROOT)) {
        paths.sorted(Comparator.reverseOrder())
                .filter(path -> !path.equals(KNOWLEDGE_ROOT))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
    }

    Files.createDirectories(KNOWLEDGE_ROOT);
}
```

---

## 7. Deterministic PostgreSQL failure injection

Create the trigger only inside the rollback test, after the original document has been ingested and snapshotted.

```java
private void installFailureTrigger() {
    jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION
                fail_knowledge_chunk_insert()
            RETURNS trigger
            LANGUAGE plpgsql
            AS $$
            BEGIN
                RAISE EXCEPTION
                    'forced knowledge chunk insertion failure';
            END;
            $$
            """);

    jdbcTemplate.execute("""
            CREATE TRIGGER
                fail_knowledge_chunk_insert_trigger
            BEFORE INSERT ON knowledge_chunks
            FOR EACH ROW
            EXECUTE FUNCTION fail_knowledge_chunk_insert()
            """);
}

private void dropFailureTrigger() {
    jdbcTemplate.execute("""
            DROP TRIGGER IF EXISTS
                fail_knowledge_chunk_insert_trigger
            ON knowledge_chunks
            """);

    jdbcTemplate.execute("""
            DROP FUNCTION IF EXISTS
                fail_knowledge_chunk_insert()
            """);
}
```

This failure occurs after the source checksum has been updated and after existing chunks have been deleted, but before replacement chunks complete. The service transaction must restore both the old source checksum and the old chunk rows.

Always call `dropFailureTrigger()` in a `finally` block.

---

## 8. Exact database snapshots

Compare exact state, not only positive counts.

```java
private SourceSnapshot snapshot(String sourcePath) {
    String sourceChecksum = jdbcTemplate.queryForObject(
            """
            SELECT encode(content_sha256, 'hex')
            FROM knowledge_sources
            WHERE source_path = ?
            """,
            String.class,
            sourcePath);

    List<ChunkSnapshot> chunks = jdbcTemplate.query(
            """
            SELECT
                chunk.ordinal,
                COALESCE(
                    array_to_json(chunk.heading_path)::text,
                    '[]'
                ) AS heading_path,
                encode(chunk.content_sha256, 'hex')
                    AS content_checksum,
                chunk.content
            FROM knowledge_chunks chunk
            JOIN knowledge_sources source
              ON source.id = chunk.source_id
            WHERE source.source_path = ?
            ORDER BY chunk.ordinal
            """,
            (resultSet, rowNumber) -> new ChunkSnapshot(
                    resultSet.getInt("ordinal"),
                    resultSet.getString("heading_path"),
                    resultSet.getString("content_checksum"),
                    resultSet.getString("content")),
            sourcePath);

    return new SourceSnapshot(
            sourceChecksum,
            List.copyOf(chunks));
}

private record SourceSnapshot(
        String sourceChecksum,
        List<ChunkSnapshot> chunks) {
}

private record ChunkSnapshot(
        int ordinal,
        String headingPath,
        String contentChecksum,
        String content) {
}
```

---

## 9. Required integration assertions

### 9.1 Exact idempotency

```java
@Test
void unchangedReingestionPreservesExactState()
        throws IOException {

    writeMarkdown(
            "guide.md",
            "# Guide\n\nStable content.");

    ingestionService.ingest();
    SourceSnapshot before = snapshot("guide.md");

    ingestionService.ingest();
    SourceSnapshot after = snapshot("guide.md");

    assertThat(after).isEqualTo(before);

    assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM knowledge_sources",
            Integer.class))
            .isEqualTo(1);

    assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM knowledge_chunks",
            Integer.class))
            .isEqualTo(before.chunks().size());
}
```

### 9.2 Changed content replaces chunks and preserves source identity

Capture the source ID before and after. Assert:

- same source ID;
- different source checksum;
- exact old snapshot differs from new snapshot;
- new chunk contents contain the replacement text;
- no old-only chunk content remains;
- ordinals are exactly `0..n-1`.

### 9.3 Rollback after replacement begins

```java
@Test
void failedReplacementRollsBackChecksumAndChunks()
        throws IOException {

    Path source = writeMarkdown(
            "guide.md",
            "# Original\n\nOriginal stable body.");

    ingestionService.ingest();
    SourceSnapshot before = snapshot("guide.md");

    Files.writeString(
            source,
            "# Replacement\n\nReplacement body.",
            StandardCharsets.UTF_8);

    installFailureTrigger();
    try {
        assertThatThrownBy(ingestionService::ingest)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Failed to ingest knowledge documents");
    } finally {
        dropFailureTrigger();
    }

    SourceSnapshot after = snapshot("guide.md");
    assertThat(after).isEqualTo(before);
}
```

The test is invalid if it manually creates the service, throws before database replacement begins, or merely verifies that a normal update succeeds.

### 9.4 Heading paths and ordinals

Use Markdown with nested headings and assert expected persisted paths, not only non-null arrays.

Example input:

```markdown
# Building

Intro.

## Roof

Roof details.

### Drainage

Drainage details.
```

Expected paths must include the corresponding hierarchy for the emitted chunks.

---

## 10. Unit tests

The unit test should cover production behavior that does not require PostgreSQL.

At minimum:

```java
@Test
void sha256IsDeterministicAndUsesProductionMethod() {
    byte[] first = KnowledgeIngestionService.sha256(
            "same".getBytes(StandardCharsets.UTF_8));

    byte[] second = KnowledgeIngestionService.sha256(
            "same".getBytes(StandardCharsets.UTF_8));

    byte[] different = KnowledgeIngestionService.sha256(
            "different".getBytes(StandardCharsets.UTF_8));

    assertThat(first).containsExactly(second);
    assertThat(first).isNotEqualTo(different);
    assertThat(first).hasSize(32);
}
```

Do not duplicate a private checksum helper inside the test.

---

## 11. Migration checks

Keep the migration focused and deterministic.

Recommended constraints:

```sql
CHECK (octet_length(content_sha256) = 32)
```

on source and chunk checksums, and:

```sql
CHECK (ordinal >= 0)
```

on chunk ordinals.

For a new, uncommitted Flyway migration, prefer plain `CREATE TABLE` over `CREATE TABLE IF NOT EXISTS`; Flyway should fail on drift rather than silently accepting a partially different schema.

Do not add unrelated pgvector or RAG retrieval work in Task 02.

---

## 12. Acceptance checklist

The task is ready for Codex review only when all are true:

- `KnowledgeIngestionServiceIT` autowires the real service.
- The service is an actual Spring transactional proxy.
- No test manually constructs the service.
- No Mockito, `@SpyBean`, AOP, reflection, subclass, or production failure hook exists.
- SQL arrays use Spring's transaction-bound connection.
- Unchanged ingestion preserves exact checksum, source identity, chunk count, chunk checksums, ordinals, headings, and contents.
- Changed ingestion replaces exact persisted chunk state.
- The rollback test fails during chunk insertion and restores the exact previous checksum and chunks.
- The official task gate is green.
- The report includes the gate command, exit code, and test totals.
- No Git write was performed.

Final command:

```bash
./scripts/task-gate.sh task-02-ingestion
```
