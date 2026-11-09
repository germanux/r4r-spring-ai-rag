package com.riansares.r4r.vector.pgvector;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.DeterministicEmbeddingModel;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure adapter/model contracts extracted from the real PostgreSQL integration suite. */
class PgVectorKnowledgeStoreTest {
    private final EmbeddingModel embeddingModel = new DeterministicEmbeddingModel();
    private final VectorStore vectors = mock(VectorStore.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PgVectorKnowledgeStore store = new PgVectorKnowledgeStore(vectors, jdbc);

    @AfterEach void neverUsesInfrastructure() { verifyNoInteractions(vectors, jdbc); }

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

}
