package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.ai.model.embedding=none")
@ActiveProfiles("test")
@Import(PgVectorTestConfiguration.class)
class PgVectorKnowledgeStoreIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PgVectorKnowledgeStore store;

    @BeforeEach
    void clearVectorStore() {
        jdbcTemplate.execute("TRUNCATE TABLE vector_store");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE TABLE vector_store");
    }

    @Test
    void flywayCreatesVector768AndHnswCosineIndex() {
        String vectorType = jdbcTemplate.queryForObject("""
                SELECT format_type(attribute.atttypid, attribute.atttypmod)
                FROM pg_attribute attribute
                JOIN pg_class table_definition
                  ON table_definition.oid = attribute.attrelid
                JOIN pg_namespace schema_definition
                  ON schema_definition.oid = table_definition.relnamespace
                WHERE schema_definition.nspname = 'public'
                  AND table_definition.relname = 'vector_store'
                  AND attribute.attname = 'embedding'
                  AND attribute.attnum > 0
                """, String.class);

        String idType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'vector_store'
                  AND column_name = 'id'
                """, String.class);

        String indexDefinition = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'vector_store'
                  AND indexname = 'idx_vector_store_embedding'
                """, String.class);

        String flywayVersion = jdbcTemplate.queryForObject("""
                SELECT version
                FROM flyway_schema_history
                WHERE success
                ORDER BY installed_rank DESC
                LIMIT 1
                """, String.class);

        assertThat(vectorType).isEqualTo("vector(768)");
        assertThat(idType).isEqualTo("uuid");
        assertThat(indexDefinition)
                .containsIgnoringCase("USING hnsw")
                .contains("vector_cosine_ops");
        assertThat(flywayVersion).isEqualTo("3");
    }

    @Test
    void reindexUpdatesStableRowsAndRemovesOnlyStaleSourceRows() {
        store.index(List.of(
                chunk("source-a.md", 0, "Alpha original"),
                chunk("source-a.md", 1, "Beta original"),
                chunk("source-a.md", 2, "Obsolete chunk"),
                chunk("source-b.md", 0, "Preserved source")));

        UUID sourceAOrdinalZeroId = storedId("source-a.md", 0);

        store.replaceSource("source-a.md", List.of(
                chunk("source-a.md", 0, "Alpha replacement"),
                chunk("source-a.md", 1, "Beta replacement")));

        assertThat(countForSource("source-a.md")).isEqualTo(2);
        assertThat(countForSource("source-b.md")).isEqualTo(1);
        assertThat(storedId("source-a.md", 0))
                .isEqualTo(sourceAOrdinalZeroId);

        assertThat(storedContent("source-a.md", 0))
                .isEqualTo("Alpha replacement");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM vector_store
                WHERE metadata->>'source' = 'source-a.md'
                  AND (metadata->>'ordinal')::int = 2
                """, Long.class)).isZero();

        store.replaceSource("source-a.md", List.of());

        assertThat(countForSource("source-a.md")).isZero();
        assertThat(countForSource("source-b.md")).isEqualTo(1);
    }

    @Test
    void canonicalIdentitySeparatesAmbiguousFieldBoundaries() {
        store.index(List.of(
                new MarkdownChunk(
                        "ab",
                        List.of("c"),
                        0,
                        "First"),
                new MarkdownChunk(
                        "a",
                        List.of("bc"),
                        0,
                        "Second")));

        UUID first = storedId("ab", 0);
        UUID second = storedId("a", 0);

        assertThat(first).isNotEqualTo(second);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store",
                Long.class)).isEqualTo(2);
    }

    @Test
    void similaritySearchUsesRealPgvectorAndPreservesExactChunkData() {
        store.index(List.of(
                new MarkdownChunk(
                        "guide.md",
                        List.of("Building", "Roof"),
                        1,
                        "Roof drainage installation details."),
                new MarkdownChunk(
                        "java.md",
                        List.of("Language"),
                        0,
                        "Java virtual threads and records."),
                new MarkdownChunk(
                        "garden.md",
                        List.of("Plants"),
                        0,
                        "Tomatoes need sunlight and water.")));

        List<MarkdownChunk> results =
                store.search("roof drainage", 2, 0.1);

        assertThat(results).isNotEmpty();

        MarkdownChunk first = results.get(0);
        assertThat(first.source()).isEqualTo("guide.md");
        assertThat(first.headingPath())
                .containsExactly("Building", "Roof");
        assertThat(first.index()).isEqualTo(1);
        assertThat(first.content())
                .isEqualTo("Roof drainage installation details.");
    }

    @Test
    void rejectsInvalidSearchAndCrossSourceReplacement() {
        assertThatThrownBy(() ->
                store.search(" ", 1, 0.0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                store.search("query", 0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                store.search("query", 1, 1.1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                store.replaceSource(
                        "source-a.md",
                        List.of(chunk(
                                "source-b.md",
                                0,
                                "Wrong source"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MarkdownChunk chunk(
            String source,
            int ordinal,
            String content) {

        return new MarkdownChunk(
                source,
                List.of("Section"),
                ordinal,
                content);
    }

    private long countForSource(String source) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM vector_store
                WHERE metadata->>'source' = ?
                """, Long.class, source);
    }

    private UUID storedId(String source, int ordinal) {
        String id = jdbcTemplate.queryForObject("""
                SELECT id::text
                FROM vector_store
                WHERE metadata->>'source' = ?
                  AND (metadata->>'ordinal')::int = ?
                """, String.class, source, ordinal);

        return UUID.fromString(id);
    }

    private String storedContent(String source, int ordinal) {
        return jdbcTemplate.queryForObject("""
                SELECT content
                FROM vector_store
                WHERE metadata->>'source' = ?
                  AND (metadata->>'ordinal')::int = ?
                """, String.class, source, ordinal);
    }

    @Test
    void reindexWithIdenticalContentPreservesRowCountAndIds() {
        List<MarkdownChunk> chunks = List.of(
                chunk("reindex-source.md", 0, "Alpha"),
                chunk("reindex-source.md", 1, "Beta"));

        store.index(chunks);

        UUID id0Before = storedId("reindex-source.md", 0);
        UUID id1Before = storedId("reindex-source.md", 1);
        long countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'source' = ?",
                Long.class, "reindex-source.md");

        store.index(chunks);

        assertThat(storedId("reindex-source.md", 0)).isEqualTo(id0Before);
        assertThat(storedId("reindex-source.md", 1)).isEqualTo(id1Before);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'source' = ?",
                Long.class, "reindex-source.md")).isEqualTo(countBefore);
    }

    @Test
    void stableIdentitySameChunkKeepsIdWhenReindexed() {
        MarkdownChunk chunk = new MarkdownChunk(
                "stable-id-source.md",
                List.of("Heading"),
                5,
                "Identical content");

        store.index(List.of(chunk));

        UUID firstId = storedId("stable-id-source.md", 5);

        store.index(List.of(chunk));

        assertThat(storedId("stable-id-source.md", 5)).isEqualTo(firstId);
    }

    @Test
    void stableIdentityContentChangeKeepsSameId() {
        MarkdownChunk chunk1 = new MarkdownChunk(
                "content-change-source.md",
                List.of("Section"),
                0,
                "Original content");

        store.index(List.of(chunk1));

        UUID originalId = storedId("content-change-source.md", 0);

        MarkdownChunk chunk2 = new MarkdownChunk(
                "content-change-source.md",
                List.of("Section"),
                0,
                "Different content but same identity");

        store.replaceSource("content-change-source.md", List.of(chunk2));

        assertThat(storedId("content-change-source.md", 0)).isEqualTo(originalId);
    }

    @Test
    void stableIdentityChangingSourceChangesId() {
        MarkdownChunk chunkA = new MarkdownChunk(
                "source-alpha.md",
                List.of("Section"),
                0,
                "Content");

        store.index(List.of(chunkA));

        UUID idA = storedId("source-alpha.md", 0);

        MarkdownChunk chunkB = new MarkdownChunk(
                "source-beta.md",
                List.of("Section"),
                0,
                "Same content different source");

        store.index(List.of(chunkB));

        assertThat(storedId("source-beta.md", 0)).isNotEqualTo(idA);
    }

    @Test
    void stableIdentityChangingHeadingPathChangesId() {
        MarkdownChunk chunk1 = new MarkdownChunk(
                "heading-source.md",
                List.of("Section", "Subsection"),
                0,
                "Content");

        store.index(List.of(chunk1));

        UUID idWithSubsection = storedId("heading-source.md", 0);

        MarkdownChunk chunk2 = new MarkdownChunk(
                "heading-source.md",
                List.of("Section"),
                0,
                "Same content different heading path");

        store.replaceSource("heading-source.md", List.of(chunk2));

        assertThat(storedId("heading-source.md", 0)).isNotEqualTo(idWithSubsection);
    }

    @Test
    void stableIdentityChangingOrdinalChangesId() {
        MarkdownChunk chunk0 = new MarkdownChunk(
                "ordinal-source.md",
                List.of("Section"),
                0,
                "Content");

        store.index(List.of(chunk0));

        UUID id0 = storedId("ordinal-source.md", 0);

        MarkdownChunk chunk1 = new MarkdownChunk(
                "ordinal-source.md",
                List.of("Section"),
                1,
                "Same content different ordinal");

        store.replaceSource("ordinal-source.md", List.of(chunk1));

        assertThat(storedId("ordinal-source.md", 1)).isNotEqualTo(id0);
    }

    @Test
    void staleDeletionRemovesOnlyReplacedSourceRows() {
        store.index(List.of(
                chunk("source-a.md", 0, "Alpha"),
                chunk("source-a.md", 1, "Beta"),
                chunk("source-b.md", 0, "Preserved")));

        UUID sourceAId0 = storedId("source-a.md", 0);
        UUID sourceBId0 = storedId("source-b.md", 0);

        store.replaceSource("source-a.md", List.of(
                chunk("source-a.md", 0, "Alpha replacement"),
                chunk("source-a.md", 1, "Beta replacement")));

        assertThat(storedContent("source-a.md", 0)).isEqualTo("Alpha replacement");
        assertThat(storedContent("source-a.md", 1)).isEqualTo("Beta replacement");
        assertThat(storedId("source-a.md", 0)).isEqualTo(sourceAId0);
        assertThat(countForSource("source-b.md")).isEqualTo(1);
        assertThat(storedId("source-b.md", 0)).isEqualTo(sourceBId0);
    }

    @Test
    void topKLimitIsEnforced() {
        store.index(List.of(
                new MarkdownChunk("doc1.md", List.of(), 0, "apple banana cherry date"),
                new MarkdownChunk("doc2.md", List.of(), 0, "apple banana cherry elderberry"),
                new MarkdownChunk("doc3.md", List.of(), 0, "apple banana fig grape"),
                new MarkdownChunk("doc4.md", List.of(), 0, "apple honeydew kiwi lemon")));

        List<MarkdownChunk> results = store.search("apple banana", 2, 0.0);

        assertThat(results).hasSize(2);
    }

    @Test
    void thresholdFilteringExcludesBelowMinScore() {
        store.index(List.of(
                new MarkdownChunk("high.md", List.of(), 0, "apple banana cherry"),
                new MarkdownChunk("low.md", List.of(), 0, "xyz abc def")));

        List<MarkdownChunk> permissive = store.search("apple banana", 10, 0.0);
        List<MarkdownChunk> strict = store.search("apple banana", 10, 0.9);

        assertThat(permissive).anySatisfy(chunk -> assertThat(chunk.source()).isEqualTo("high.md"));
        assertThat(strict).noneSatisfy(chunk -> assertThat(chunk.source()).isEqualTo("low.md"));
    }

    @Test
    void schemaAssertsMetadataColumnAndPrimaryKey() {
        String metadataType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'vector_store'
                  AND column_name = 'metadata'
                """, String.class);

        String pkDefinition = jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid = 'vector_store'::regclass
                  AND contype = 'p'
                """, String.class);

        assertThat(metadataType).isEqualTo("jsonb");
        assertThat(pkDefinition).contains("id");
    }

    @Test
    void rejectsNullChunkList() {
        assertThatThrownBy(() -> store.index((List<MarkdownChunk>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunks");
    }

    @Test
    void rejectsNullChunkEntry() {
        assertThatThrownBy(() -> store.index(List.of(
                chunk("source.md", 0, "Valid"),
                null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankSourceInIndex() {
        MarkdownChunk chunkWithBlankSource = new MarkdownChunk(
                "   ",
                List.of("Section"),
                0,
                "Content");

        assertThatThrownBy(() -> store.index(List.of(chunkWithBlankSource)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullQuery() {
        assertThatThrownBy(() -> store.search(null, 1, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
    }

    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> store.search("   ", 1, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveTopK() {
        assertThatThrownBy(() -> store.search("query", 0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> store.search("query", -1, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMinScoreNaN() {
        assertThatThrownBy(() -> store.search("query", 1, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMinScoreInfinity() {
        assertThatThrownBy(() -> store.search("query", 1, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> store.search("query", 1, Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMinScoreBelowZero() {
        assertThatThrownBy(() -> store.search("query", 1, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMinScoreAboveOne() {
        assertThatThrownBy(() -> store.search("query", 1, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeOrdinalInIndex() {
        assertThatThrownBy(() -> store.index(List.of(
                new MarkdownChunk(
                        "source.md",
                        List.of("Section"),
                        -1,
                        "Content"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullSourceInReplaceSource() {
        assertThatThrownBy(() -> store.replaceSource(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }

    @Test
    void rejectsBlankSourceInReplaceSource() {
        assertThatThrownBy(() -> store.replaceSource("   ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullChunksInReplaceSource() {
        assertThatThrownBy(() -> store.replaceSource("source.md", (List<MarkdownChunk>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunks");
    }

    @Test
    void retrievalFailsForNullDocument() {
        assertThatThrownBy(() -> store.fromDocument((Document) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document");
    }

    @Test
    void retrievalFailsForNullText() {
        assertThatThrownBy(() -> {
            Document document = Document.builder()
                    .id("test-id")
                    .text(null)
                    .metadata(Map.of())
                    .build();
            store.fromDocument(document);
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    @Test
    void retrievalFailsForNullMetadata() {
        assertThatThrownBy(() -> {
            Document document = Document.builder()
                    .id("test-id")
                    .text("content")
                    .metadata(null)
                    .build();
            store.fromDocument(document);
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void retrievalFailsForMissingSourceMetadata() {
        Document document = Document.builder()
                .id("test-id")
                .text("content")
                .metadata(Map.of(
                        PgVectorKnowledgeStore.HEADING_PATH_METADATA, List.of(),
                        PgVectorKnowledgeStore.ORDINAL_METADATA, 0))
                .build();

        assertThatThrownBy(() -> store.fromDocument(document))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'source'");
    }

    @Test
    void retrievalFailsForBlankSourceMetadata() {
        Document document = Document.builder()
                .id("test-id")
                .text("content")
                .metadata(Map.of(
                        PgVectorKnowledgeStore.SOURCE_METADATA, "   ",
                        PgVectorKnowledgeStore.HEADING_PATH_METADATA, List.of(),
                        PgVectorKnowledgeStore.ORDINAL_METADATA, 0))
                .build();

        assertThatThrownBy(() -> store.fromDocument(document))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'source'");
    }

    @Test
    void retrievalFailsForMissingHeadingPathMetadata() {
        Document document = Document.builder()
                .id("test-id")
                .text("content")
                .metadata(Map.of(
                        PgVectorKnowledgeStore.SOURCE_METADATA, "source.md",
                        PgVectorKnowledgeStore.ORDINAL_METADATA, 0))
                .build();

        assertThatThrownBy(() -> store.fromDocument(document))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'headingPath'");
    }

    @Test
    void retrievalFailsForMissingOrdinalMetadata() {
        Document document = Document.builder()
                .id("test-id")
                .text("content")
                .metadata(Map.of(
                        PgVectorKnowledgeStore.SOURCE_METADATA, "source.md",
                        PgVectorKnowledgeStore.HEADING_PATH_METADATA, List.of()))
                .build();

        assertThatThrownBy(() -> store.fromDocument(document))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'ordinal'");
    }

    @Test
    void retrievalFailsForNonIntegerOrdinal() {
        Document document = Document.builder()
                .id("test-id")
                .text("content")
                .metadata(Map.of(
                        PgVectorKnowledgeStore.SOURCE_METADATA, "source.md",
                        PgVectorKnowledgeStore.HEADING_PATH_METADATA, List.of(),
                        PgVectorKnowledgeStore.ORDINAL_METADATA, 1.5))
                .build();

        assertThatThrownBy(() -> store.fromDocument(document))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'ordinal'");
    }

    @Test
    void retrievalFailsForNegativeOrdinal() {
        Document document = Document.builder()
                .id("test-id")
                .text("content")
                .metadata(Map.of(
                        PgVectorKnowledgeStore.SOURCE_METADATA, "source.md",
                        PgVectorKnowledgeStore.HEADING_PATH_METADATA, List.of(),
                        PgVectorKnowledgeStore.ORDINAL_METADATA, -1))
                .build();

        assertThatThrownBy(() -> store.fromDocument(document))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'ordinal'");
    }
}
