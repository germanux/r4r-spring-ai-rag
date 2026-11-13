package com.riansares.r4r.ingestion;

import com.riansares.r4r.chunking.HeadingMarkdownChunker;
import com.riansares.r4r.document.*;
import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class IndexedIngestionTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MarkdownDocumentLoader loader = mock(MarkdownDocumentLoader.class);
    PgVectorKnowledgeStore vectors = mock(PgVectorKnowledgeStore.class);
    EmbeddingModel embeddings = mock(EmbeddingModel.class);
    KnowledgeIngestionService service = new KnowledgeIngestionService(jdbc, loader, new HeadingMarkdownChunker(2000));

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        ObjectProvider<PgVectorKnowledgeStore> vp = mock(ObjectProvider.class);
        ObjectProvider<EmbeddingModel> ep = mock(ObjectProvider.class);
        when(vp.getObject()).thenReturn(vectors); when(ep.getObject()).thenReturn(embeddings);
        ReflectionTestUtils.setField(service, "indexedStore", vp);
        ReflectionTestUtils.setField(service, "embeddingModel", ep);
        when(loader.loadBilingual()).thenReturn(List.of(
                new KnowledgeDocument("es/R4R_00_A.md", "---\nlanguage: es\n---\n# A\nEvidence")));
        when(jdbc.queryForMap(anyString())).thenReturn(Map.of("server","172.20.0.5","read_only","off"));
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("192.168.1.203");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyString())).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("vector(768)");
        when(embeddings.embed(anyString())).thenReturn(new float[768]);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenAnswer(i ->
                ((String)i.getArgument(0)).contains("FULL JOIN") ? 0L : 1L);
        when(jdbc.queryForObject(contains("RETURNING id"), eq(Long.class), anyString(), any(byte[].class))).thenReturn(1L);
    }

    @Test void preflightDoesNotWriteOrIndex() throws Exception {
        assertThat(service.ingestIndexed(true, false)).containsEntry("checkOnly", true).containsEntry("expectedChunks", 1);
        verify(jdbc, never()).update(anyString());
        verify(jdbc, never()).execute(anyString());
        verifyNoInteractions(vectors);
    }
    @Test void staleSourcesRequireExplicitReplacement() {
        when(jdbc.queryForList(contains("SELECT source_path"), eq(String.class))).thenReturn(List.of("old.md"));
        assertThatThrownBy(() -> service.ingestIndexed(false, false)).hasMessageContaining("replace-all");
        verify(jdbc, never()).update(anyString()); verifyNoInteractions(vectors);
    }
    @Test void completeWorkflowIndexesActualChunksWithoutFrontmatter() throws Exception {
        assertThat(service.ingestIndexed(false, false)).containsEntry("success", true).containsEntry("vectors", 1L);
        verify(vectors).index(argThat(parts -> parts.size()==1 && !parts.getFirst().content().contains("language:")));
    }
    @Test void embeddingFailurePropagatesBeforeAnyDatabaseMutation() {
        when(embeddings.embed(anyString())).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(() -> service.ingestIndexed(false, true)).hasMessage("offline");
        verify(jdbc, never()).execute(anyString()); verify(jdbc, never()).update(anyString());
    }
    @Test void vectorFailureCannotReportSuccess() {
        doThrow(new IllegalStateException("vector write failed")).when(vectors).index(anyList());
        assertThatThrownBy(() -> service.ingestIndexed(false, false)).hasMessage("vector write failed");
    }
    @Test void wrongDatabaseHostIsRejectedBeforeEmbedding() {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("localhost");
        assertThatThrownBy(() -> service.ingestIndexed(false, false)).hasMessageContaining("NAS");
        verifyNoInteractions(embeddings, vectors);
    }
}
