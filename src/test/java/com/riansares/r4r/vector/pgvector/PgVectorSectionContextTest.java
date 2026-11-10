package com.riansares.r4r.vector.pgvector;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PgVectorSectionContextTest {
    @Test void headingUsesBoundedChildrenFromItsOwnSourceWithoutAnotherEmbedding() {
        var vectors = mock(VectorStore.class);
        var jdbc = mock(JdbcTemplate.class);
        var heading = document("## Presupuesto");
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(heading));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("section"), eq("faq.md"),
                eq("[\"FAQ\",\"Presupuesto\"]"), eq(2)))
                .thenReturn(List.of("### Orientación\nUbicación, fotografías y medidas.", "x".repeat(6000)));

        var results = new PgVectorKnowledgeStore(vectors, jdbc).search("datos", 5, .63);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().content()).contains("Ubicación, fotografías y medidas.")
                .doesNotContain("x".repeat(6000));
        assertThat(results.getFirst().source()).isEqualTo("faq.md");
        assertThat(results.getFirst().headingPath()).containsExactly("FAQ", "Presupuesto");
        verify(vectors, times(1)).similaritySearch(argThat((SearchRequest r) ->
                r.getTopK() == 20 && r.getSimilarityThreshold() == .63 && r.getQuery().equals("datos")));
        verify(jdbc).queryForList(contains("metadata->>'source' = ?"), eq(String.class),
                eq("section"), eq("faq.md"), eq("[\"FAQ\",\"Presupuesto\"]"), eq(2));
        verifyNoMoreInteractions(jdbc, vectors);
    }

    @Test void completeChunksDoNotReadAdditionalContext() {
        var vectors = mock(VectorStore.class);
        var jdbc = mock(JdbcTemplate.class);
        when(vectors.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(document("### Orientación\nUbicación y medidas.")));
        assertThat(new PgVectorKnowledgeStore(vectors, jdbc).search("datos", 5, .63)
                .getFirst().content()).isEqualTo("### Orientación\nUbicación y medidas.");
        verifyNoInteractions(jdbc);
    }

    private static Document document(String text) {
        return Document.builder().id("section").text(text).score(.7)
                .metadata(Map.of("source", "faq.md", "headingPath", List.of("FAQ", "Presupuesto"), "ordinal", 1))
                .build();
    }
}
