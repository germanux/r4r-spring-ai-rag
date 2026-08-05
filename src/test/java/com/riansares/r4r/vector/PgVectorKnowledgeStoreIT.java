package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

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

    @Autowired
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void clearVectorStore() {
        jdbcTemplate.execute("TRUNCATE TABLE vector_store");
    }

    @Test
    void flywayCreatesExpectedPgVectorSchema() {
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

        String idType = columnType("id");
        String metadataType = columnType("metadata");

        String primaryKey = jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid = 'public.vector_store'::regclass
                  AND contype = 'p'
                """, String.class);

        String embeddingIndex = jdbcTemplate.queryForObject("""
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
        assertThat(metadataType).isEqualTo("jsonb");
        assertThat(primaryKey).contains("PRIMARY KEY").contains("id");
        assertThat(embeddingIndex.toLowerCase())
                .contains("using hnsw")
                .contains("vector_cosine_ops");
        assertThat(flywayVersion).isEqualTo("3");
    }

    @Test
    void deterministicEmbeddingModelIsLocalStableAnd768Dimensional() {
        assertThat(embeddingModel)
                .isInstanceOf(DeterministicEmbeddingModel.class);

        DeterministicEmbeddingModel model =
                (DeterministicEmbeddingModel) embeddingModel;

        float[] first = model.embed(document("same text"));
        float[] second = model.embed(document("same text"));
        float[] different = model.embed(document("unrelated tokens"));

        assertThat(model.dimensions()).isEqualTo(768);
        assertThat(first).hasSize(768);
        assertThat(second).containsExactly(first);
        assertThat(Arrays.equals(first, different)).isFalse();
    }

    @Test
    void deterministicEmbeddingModelProducesExact768DimensionalVectors() {
        DeterministicEmbeddingModel model =
                (DeterministicEmbeddingModel) embeddingModel;

        // Test dimensions() returns 768
        assertThat(model.dimensions()).isEqualTo(768);

        // Test produced vector length is exactly 768
        float[] vector = model.embed(document("test"));
        assertThat(vector).hasSize(768);

        // Test equal text produces identical vectors
        float[] v1 = model.embed(document("identical content"));
        float[] v2 = model.embed(document("identical content"));
        assertThat(v2).containsExactly(v1);

        // Test controlled relevant/irrelevant texts produce distinguishable vectors
        float[] relevant = model.embed(document("apple banana cherry date"));
        float[] irrelevant = model.embed(document("xyz abc def ghi"));
        
        // Vectors should not be equal (cosine similarity < 1.0)
        assertThat(relevant).isNotEqualTo(irrelevant);
    }

    @Test
    void repeatedIndexingKeepsRowCountAndLogicalIds() {
        List<MarkdownChunk> chunks = List.of(
                chunk("repeat.md", 0, "Alpha"),
                chunk("repeat.md", 1, "Beta"));

        store.index(chunks);
        List<StoredRow> before = snapshotAllRows();

        store.index(chunks);
        List<StoredRow> after = snapshotAllRows();

        assertThat(after).isEqualTo(before);
        assertThat(after).hasSize(2);
    }

    @Test
    void contentChangeKeepsIdAndReplacesStoredContent() {
        store.index(List.of(chunk("content.md", 0, "Original")));
        UUID originalId = storedId("content.md", 0);

        store.replaceSource(
                "content.md",
                List.of(chunk("content.md", 0, "Replacement")));

        assertThat(storedId("content.md", 0)).isEqualTo(originalId);
        assertThat(storedContent("content.md", 0))
                .isEqualTo("Replacement");
    }

    @Test
    void replacementRemovesStaleRowsAndPreservesOtherSources() {
        store.index(List.of(
                chunk("source-a.md", 0, "A0"),
                chunk("source-a.md", 1, "A1"),
                chunk("source-a.md", 2, "A2 stale"),
                chunk("source-b.md", 0, "B0 preserved")));

        UUID sourceBId = storedId("source-b.md", 0);

        store.replaceSource("source-a.md", List.of(
                chunk("source-a.md", 0, "A0 replacement"),
                chunk("source-a.md", 1, "A1 replacement")));

        assertThat(countForSource("source-a.md")).isEqualTo(2);
        assertThat(countForSource("source-b.md")).isEqualTo(1);
        assertThat(storedId("source-b.md", 0)).isEqualTo(sourceBId);
        assertThat(ordinalExists("source-a.md", 2)).isFalse();

        store.replaceSource("source-a.md", List.of());

        assertThat(countForSource("source-a.md")).isZero();
        assertThat(countForSource("source-b.md")).isEqualTo(1);
    }

    @Test
    void stableIdentityIsBoundarySafeAndDependsOnSourceHeadingAndOrdinal() {
        MarkdownChunk first = new MarkdownChunk(
                "boundary.md",
                List.of("ab", "c"),
                0,
                "first-content");
        MarkdownChunk second = new MarkdownChunk(
                "boundary.md",
                List.of("a", "bc"),
                0,
                "second-content");

        store.index(List.of(first, second));

        UUID firstId = storedIdByContent("first-content");
        UUID secondId = storedIdByContent("second-content");
        assertThat(firstId).isNotEqualTo(secondId);

        store.index(List.of(
                chunk("source-one.md", 0, "source-one"),
                chunk("source-two.md", 0, "source-two"),
                chunk("ordinal.md", 0, "ordinal-zero"),
                chunk("ordinal.md", 1, "ordinal-one")));

        assertThat(storedId("source-one.md", 0))
                .isNotEqualTo(storedId("source-two.md", 0));
        assertThat(storedId("ordinal.md", 0))
                .isNotEqualTo(storedId("ordinal.md", 1));
    }

    @Test
    void duplicateIdentityInLaterSourceFailsBeforeAnyMutation() {
        store.index(List.of(
                chunk("source-a.md", 0, "Original A"),
                chunk("source-b.md", 0, "Original B")));
        List<StoredRow> before = snapshotAllRows();

        List<MarkdownChunk> invalidRequest = List.of(
                chunk("source-a.md", 0, "Valid replacement A"),
                chunk("source-b.md", 0, "Duplicate B first"),
                chunk("source-b.md", 0, "Duplicate B second"));

        assertThatThrownBy(() -> store.index(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate logical chunk identity");

        assertThat(snapshotAllRows()).isEqualTo(before);
    }

    @Test
    void crossSourcePrevalidationWithExistingDataProvesNoMutation() {
        // Seed existing rows for two sources
        store.index(List.of(
                chunk("existing-a.md", 0, "Original A0"),
                chunk("existing-b.md", 0, "Original B0")));
        
        List<StoredRow> beforeState = snapshotAllRows();

        // Submit valid earlier source followed by later source with duplicate logical identities
        List<MarkdownChunk> invalidRequest = List.of(
                chunk("existing-a.md", 0, "Valid replacement A0"),
                chunk("existing-b.md", 0, "Duplicate B0 first"),
                chunk("existing-b.md", 0, "Duplicate B0 second"));

        assertThatThrownBy(() -> store.index(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate logical chunk identity");

        // Compare complete pre-existing state for all involved sources
        List<StoredRow> afterState = snapshotAllRows();
        
        // Verify no mutation occurred - before and after should be identical
        assertThat(afterState).isEqualTo(beforeState);
    }

    @Test
    void similaritySearchPreservesExactCitationData() {
        MarkdownChunk expected = new MarkdownChunk(
                "guide.md",
                List.of("Building", "Roof"),
                1,
                "Roof drainage installation details.");

        store.index(List.of(
                expected,
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
                store.search("roof drainage", 1, 0.1);

        assertThat(results).containsExactly(expected);
    }

    @Test
    void topKIsEnforced() {
        store.index(List.of(
                chunk("doc1.md", 0, "apple banana cherry date"),
                chunk("doc2.md", 0, "apple banana cherry elderberry"),
                chunk("doc3.md", 0, "apple banana fig grape"),
                chunk("doc4.md", 0, "apple honeydew kiwi lemon")));

        assertThat(store.search("apple banana", 2, 0.0)).hasSize(2);
    }

    @Test
    void strictThresholdKeepsRelevantAndExcludesIrrelevantResult() {
        store.index(List.of(
                chunk("high.md", 0, "apple banana cherry"),
                chunk("low.md", 0, "xyz abc def")));

        List<MarkdownChunk> strict =
                store.search("apple banana", 10, 0.75);

        // Verify we got results
        assertThat(strict).isNotEmpty();
        
        List<String> sources = strict.stream()
                .map(MarkdownChunk::source)
                .toList();

        assertThat(sources)
                .contains("high.md")
                .doesNotContain("low.md");
    }

    @Test
    void invalidPublicInputsAreRejectedBeforeVectorStoreUse() {
        assertThatThrownBy(() -> store.index(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunks");

        List<MarkdownChunk> withNull = new ArrayList<>();
        withNull.add(chunk("valid.md", 0, "Valid"));
        withNull.add(null);
        
        assertThatThrownBy(() -> store.index(withNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk must not be null");

        MarkdownChunk blankSource = new MarkdownChunk(
                "   ", List.of(), 0, "content");
        assertThatThrownBy(() -> store.index(List.of(blankSource)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk.source");

        MarkdownChunk blankContent = new MarkdownChunk(
                "source.md", List.of(), 0, "   ");
        assertThatThrownBy(() -> store.index(List.of(blankContent)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk.content");

        assertThatThrownBy(() -> store.replaceSource(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
        assertThatThrownBy(() -> store.replaceSource(" ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.replaceSource("source.md", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunks");
        assertThatThrownBy(() -> store.replaceSource(
                "source-a.md",
                List.of(chunk("source-b.md", 0, "wrong source"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source-a.md");

        assertThatThrownBy(() -> store.search(null, 1, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.search(" ", 1, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.search("query", 0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.search("query", 1, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.search(
                "query", 1, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.search("query", 1, -0.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.search("query", 1, 1.01))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedReachableMetadataIsRejectedDefensively() {
        Document missingSource = document(Map.of(
                PgVectorKnowledgeStore.HEADING_PATH_METADATA, List.of(),
                PgVectorKnowledgeStore.ORDINAL_METADATA, 0));
        assertMetadataFailure(missingSource, "'source'");

        Document blankSource = document(Map.of(
                PgVectorKnowledgeStore.SOURCE_METADATA, "   ",
                PgVectorKnowledgeStore.HEADING_PATH_METADATA, List.of(),
                PgVectorKnowledgeStore.ORDINAL_METADATA, 0));
        assertMetadataFailure(blankSource, "'source'");

        Document missingHeadingPath = document(Map.of(
                PgVectorKnowledgeStore.SOURCE_METADATA, "source.md",
                PgVectorKnowledgeStore.ORDINAL_METADATA, 0));
        assertMetadataFailure(missingHeadingPath, "'headingPath'");

        List<Object> malformedHeadingPath = new ArrayList<>();
        malformedHeadingPath.add("valid");
        malformedHeadingPath.add(123);
        Document nonStringHeading = document(Map.of(
                PgVectorKnowledgeStore.SOURCE_METADATA, "source.md",
                PgVectorKnowledgeStore.HEADING_PATH_METADATA,
                        malformedHeadingPath,
                PgVectorKnowledgeStore.ORDINAL_METADATA, 0));
        assertMetadataFailure(nonStringHeading, "non-string");

        Document missingOrdinal = document(Map.of(
                PgVectorKnowledgeStore.SOURCE_METADATA, "source.md",
                PgVectorKnowledgeStore.HEADING_PATH_METADATA, List.of()));
        assertMetadataFailure(missingOrdinal, "'ordinal'");

        Document nonNumberOrdinal = document(Map.of(
                PgVectorKnowledgeStore.SOURCE_METADATA, "source.md",
                PgVectorKnowledgeStore.HEADING_PATH_METADATA, List.of(),
                PgVectorKnowledgeStore.ORDINAL_METADATA, "zero"));
        assertMetadataFailure(nonNumberOrdinal, "numeric");

        Document fractionalOrdinal = document(Map.of(
                PgVectorKnowledgeStore.SOURCE_METADATA, "source.md",
                PgVectorKnowledgeStore.HEADING_PATH_METADATA, List.of(),
                PgVectorKnowledgeStore.ORDINAL_METADATA, 1.5));
        assertMetadataFailure(fractionalOrdinal, "non-negative integer");

        Document negativeOrdinal = document(Map.of(
                PgVectorKnowledgeStore.SOURCE_METADATA, "source.md",
                PgVectorKnowledgeStore.HEADING_PATH_METADATA, List.of(),
                PgVectorKnowledgeStore.ORDINAL_METADATA, -1));
        assertMetadataFailure(negativeOrdinal, "non-negative integer");

        assertThatThrownBy(() -> store.fromDocument(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document");
    }

    private void assertMetadataFailure(
            Document document,
            String expectedMessage) {

        assertThatThrownBy(() -> store.fromDocument(document))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedMessage);
    }

    private String columnType(String column) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'vector_store'
                  AND column_name = ?
                """, String.class, column);
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

    private Document document(String text) {
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .metadata(Map.of())
                .build();
    }

    private Document document(Map<String, Object> metadata) {
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .text("content")
                .metadata(metadata)
                .build();
    }

    private long countForSource(String source) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM vector_store
                WHERE metadata->>'source' = ?
                """, Long.class, source);
    }

    private boolean ordinalExists(String source, int ordinal) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM vector_store
                    WHERE metadata->>'source' = ?
                      AND (metadata->>'ordinal')::int = ?
                )
                """, Boolean.class, source, ordinal));
    }

    private UUID storedId(String source, int ordinal) {
        String id = jdbcTemplate.queryForObject("""
                SELECT id::text
                FROM vector_store
                WHERE metadata->>'source' = ?
                  AND (metadata->>'ordinal')::int = ?
                ORDER BY id
                LIMIT 1
                """, String.class, source, ordinal);

        return UUID.fromString(id);
    }

    private UUID storedIdByContent(String content) {
        String id = jdbcTemplate.queryForObject("""
                SELECT id::text
                FROM vector_store
                WHERE content = ?
                """, String.class, content);

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

    private List<StoredRow> snapshotAllRows() {
        return jdbcTemplate.query("""
                SELECT id::text,
                       content,
                       metadata::text,
                       embedding::text
                FROM vector_store
                ORDER BY metadata->>'source',
                         (metadata->>'ordinal')::int,
                         id
                """, (resultSet, rowNumber) -> new StoredRow(
                resultSet.getString(1),
                resultSet.getString(2),
                resultSet.getString(3),
                resultSet.getString(4)));
    }

    private record StoredRow(
            String id,
            String content,
            String metadata,
            String embedding) {
    }
}
