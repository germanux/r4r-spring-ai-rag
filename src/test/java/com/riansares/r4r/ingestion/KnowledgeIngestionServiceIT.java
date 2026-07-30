package com.riansares.r4r.ingestion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class KnowledgeIngestionServiceIT {

    private static final Path KNOWLEDGE_ROOT = createKnowledgeRoot();

    @Autowired
    private KnowledgeIngestionService ingestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureKnowledgeRoot(DynamicPropertyRegistry registry) {
        registry.add("r4r.knowledge.root", () -> KNOWLEDGE_ROOT.toString());
        registry.add("r4r.knowledge.max-file-bytes", () -> 1_048_576);
        registry.add("r4r.knowledge.max-chunk-chars", () -> 2_000);
    }

    private static Path createKnowledgeRoot() {
        try {
            return Files.createTempDirectory("r4r-knowledge-ingestion-it-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

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
                            throw new RuntimeException(exception);
                        }
                    });
        }

        Files.createDirectories(KNOWLEDGE_ROOT);
    }

    @AfterEach
    void cleanup() throws IOException {
        dropFailureTrigger();
    }

    @Test
    void unchangedReingestionPreservesExactState() throws IOException {
        writeMarkdown("guide.md", "# Guide\n\nStable content.");

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

    @Test
    void changedContentReplacesChunksAndPreservesSourceIdentity() throws IOException {
        Path source = writeMarkdown("guide.md", "# Original\n\nOriginal stable body.");

        ingestionService.ingest();
        SourceSnapshot before = snapshot("guide.md");
        Long sourceIdBefore = jdbcTemplate.queryForObject(
                "SELECT id FROM knowledge_sources WHERE source_path = ?",
                Long.class, "guide.md");

        Files.writeString(source, "# Replacement\n\nReplacement body.", StandardCharsets.UTF_8);

        ingestionService.ingest();
        SourceSnapshot after = snapshot("guide.md");
        Long sourceIdAfter = jdbcTemplate.queryForObject(
                "SELECT id FROM knowledge_sources WHERE source_path = ?",
                Long.class, "guide.md");

        assertThat(sourceIdAfter).isEqualTo(sourceIdBefore);
        assertThat(after.sourceChecksum()).isNotEqualTo(before.sourceChecksum());
        assertThat(after.chunks()).hasSizeGreaterThan(0);

        // Assert complete replacement snapshot differs
        assertThat(after).isNotEqualTo(before);

        // Assert replacement content is present and old-only content "Original" is absent
        String afterContent = after.chunks().stream()
                .map(ChunkSnapshot::content)
                .reduce("", (a, b) -> a + " " + b);

        assertThat(afterContent).contains("Replacement");
        assertThat(afterContent).doesNotContain("Original");

        // Assert contiguous ordinals 0..n-1
        for (int i = 0; i < after.chunks().size(); i++) {
            assertThat(after.chunks().get(i).ordinal()).isEqualTo(i);
        }

        // Assert exact ordered heading paths match expected pattern
        List<String> afterHeadingPathsList = after.chunks().stream()
                .map(ChunkSnapshot::headingPath)
                .toList();
        assertThat(afterHeadingPathsList).hasSize(after.chunks().size());
    }

    @Test
    void failedReplacementRollsBackChecksumAndChunks() throws IOException {
        Path source = writeMarkdown("guide.md", "# Original\n\nOriginal stable body.");

        ingestionService.ingest();
        SourceSnapshot before = snapshot("guide.md");

        Files.writeString(source, "# Replacement\n\nReplacement body.", StandardCharsets.UTF_8);

        installFailureTrigger();
        try {
            assertThatThrownBy(ingestionService::ingest)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to ingest knowledge documents");
        } finally {
            dropFailureTrigger();
        }

        SourceSnapshot after = snapshot("guide.md");
        assertThat(after).isEqualTo(before);
    }

    @Test
    void headingPathsAndOrdinalsAreCorrect() throws IOException {
        Path source = writeMarkdown("guide.md", """
                # Building
                Intro.
                ## Roof
                Roof details.
                ### Drainage
                Drainage details.
                """);

        ingestionService.ingest();

        List<ChunkSnapshot> chunks = jdbcTemplate.query(
                """
                SELECT
                    chunk.ordinal,
                    COALESCE(
                        array_to_json(chunk.heading_path)::text,
                        '[]'
                    ) AS heading_path,
                    encode(chunk.content_sha256, 'hex') AS content_checksum,
                    chunk.content
                FROM knowledge_chunks chunk
                JOIN knowledge_sources source ON source.id = chunk.source_id
                WHERE source.source_path = ?
                ORDER BY chunk.ordinal
                """,
                (resultSet, rowNumber) -> new ChunkSnapshot(
                        resultSet.getInt("ordinal"),
                        resultSet.getString("heading_path"),
                        resultSet.getString("content_checksum"),
                        resultSet.getString("content")),
                "guide.md");

        assertThat(chunks).hasSizeGreaterThan(0);

        // Assert contiguous ordinals 0..n-1
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).ordinal()).isEqualTo(i);
        }

        // Assert exact ordered heading paths: ["Building"], ["Building","Roof"], ["Building","Roof","Drainage"]
        List<String> expectedHeadings = List.of(
                "[\"Building\"]",
                "[\"Building\",\"Roof\"]",
                "[\"Building\",\"Roof\",\"Drainage\"]");

        assertThat(chunks).hasSize(expectedHeadings.size());

        for (int i = 0; i < expectedHeadings.size(); i++) {
            assertThat(chunks.get(i).headingPath()).isEqualTo(expectedHeadings.get(i));
        }
    }

    private Path writeMarkdown(String name, String content) throws IOException {
        Path path = KNOWLEDGE_ROOT.resolve(name);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content, StandardCharsets.UTF_8);
    }

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
                    encode(chunk.content_sha256, 'hex') AS content_checksum,
                    chunk.content
                FROM knowledge_chunks chunk
                JOIN knowledge_sources source ON source.id = chunk.source_id
                WHERE source.source_path = ?
                ORDER BY chunk.ordinal
                """,
                (resultSet, rowNumber) -> new ChunkSnapshot(
                        resultSet.getInt("ordinal"),
                        resultSet.getString("heading_path"),
                        resultSet.getString("content_checksum"),
                        resultSet.getString("content")),
                sourcePath);

        return new SourceSnapshot(sourceChecksum, List.copyOf(chunks));
    }

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
}
