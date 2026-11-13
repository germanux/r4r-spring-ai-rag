package com.riansares.r4r.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIngestionServiceTest {

    @Test
    void sha256IsDeterministicAndUsesProductionMethod() {
        byte[] first = KnowledgeIngestionService.sha256(
                "same".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] second = KnowledgeIngestionService.sha256(
                "same".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] different = KnowledgeIngestionService.sha256(
                "different".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(first).containsExactly(second);
        assertThat(first).isNotEqualTo(different);
        assertThat(first).hasSize(32);
    }
    @Test
    void documentaryIngestionAlsoExcludesAndSelectivelyRemovesAllThreeRepresentations() throws Exception {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var loader = org.mockito.Mockito.mock(com.riansares.r4r.document.MarkdownDocumentLoader.class);
        var chunker = org.mockito.Mockito.mock(com.riansares.r4r.chunking.HeadingMarkdownChunker.class);
        String source = "es/sources/R4R_71_REFERENCES.md";
        org.mockito.Mockito.when(loader.loadAll()).thenReturn(java.util.List.of(
                new com.riansares.r4r.document.KnowledgeDocument(source,
                        "---\ningest_into_editorial_rag: false\n---\n# References")));
        org.mockito.Mockito.when(jdbc.update("DELETE FROM knowledge_sources WHERE source_path = ?", source))
                .thenReturn(1);
        var result = new KnowledgeIngestionService(jdbc, loader, chunker).ingest(java.time.Clock.systemUTC());
        assertThat(result.discoveredSources()).isEqualTo(1);
        assertThat(result.deletedSources()).isEqualTo(1);
        assertThat(result.changedSources()).isZero();
        assertThat(result.persistedChunks()).isZero();
        var order = org.mockito.Mockito.inOrder(jdbc);
        order.verify(jdbc).update("DELETE FROM vector_store WHERE metadata->>'source' = ?", source);
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("DELETE FROM knowledge_chunks WHERE source_id IN"),
                org.mockito.ArgumentMatchers.eq(source));
        order.verify(jdbc).update("DELETE FROM knowledge_sources WHERE source_path = ?", source);
        org.mockito.Mockito.verifyNoMoreInteractions(jdbc);
        org.mockito.Mockito.verifyNoInteractions(chunker);
    }

}
