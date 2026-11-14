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
        when(loader.loadBilingualCorpus()).thenReturn(new MarkdownDocumentLoader.BilingualCorpus(List.of(
                new KnowledgeDocument("es/R4R_00_A.md", "---\nlanguage: es\n---\n# A\nEvidence")), List.of()));
        when(jdbc.queryForMap(anyString())).thenReturn(Map.of("server","172.20.0.5","read_only","off"));
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("192.168.1.203");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyString())).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("vector(1024)");
        when(embeddings.embed(anyString())).thenReturn(new float[1024]);
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
    private static final List<String> EXCLUDED = List.of(
            "es/sources/R4R_71_REFERENCIAS_TECNICAS_INTERNACIONALES_2026.md",
            "en/sources/R4R_71_INTERNATIONAL_TECHNICAL_REFERENCES_2026.md");

    private void excludeReferences() throws Exception {
        var current = loader.loadBilingualCorpus();
        when(loader.loadBilingualCorpus()).thenReturn(new MarkdownDocumentLoader.BilingualCorpus(
                current.documents(), EXCLUDED));
        when(jdbc.queryForList(contains("SELECT source_path"), eq(String.class))).thenReturn(EXCLUDED);
        when(jdbc.queryForList(contains("SELECT DISTINCT metadata"), eq(String.class))).thenReturn(EXCLUDED);
        when(jdbc.update(eq("DELETE FROM knowledge_sources WHERE source_path = ?"), anyString())).thenReturn(1);
    }

    private void unchangedSource(boolean consistent) {
        when(jdbc.queryForObject(contains("AND content_sha256 = ?"), eq(Long.class),
                eq("es/R4R_00_A.md"), any(byte[].class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("AND NOT EXISTS"), eq(Boolean.class),
                eq(1L), eq(1), eq("es/R4R_00_A.md"), eq(1), eq("es/R4R_00_A.md"), eq(1L)))
                .thenReturn(consistent);
    }

    @Test void exclusionsRemoveOnlyTheirRowsAcrossAllThreeTablesWithoutReindexingOthers() throws Exception {
        excludeReferences();
        unchangedSource(true);
        assertThat(service.ingestIndexed(false, false)).containsEntry("deletedSources", 2)
                .containsEntry("unchangedSources", 1).containsEntry("indexedSources", 0)
                .containsEntry("sources", 1L).containsEntry("chunks", 1L).containsEntry("vectors", 1L);
        var order = inOrder(jdbc);
        for (String source : EXCLUDED) {
            order.verify(jdbc).update("DELETE FROM vector_store WHERE metadata->>'source' = ?", source);
            order.verify(jdbc).update(contains("DELETE FROM knowledge_chunks WHERE source_id IN"), eq(source));
            order.verify(jdbc).update("DELETE FROM knowledge_sources WHERE source_path = ?", source);
        }
        verify(jdbc, never()).update(anyString()); // No table-wide DELETE.
        verify(jdbc, never()).queryForObject(contains("INSERT INTO knowledge_sources"), eq(Long.class),
                anyString(), any(byte[].class));
        verify(jdbc, never()).update(contains("DELETE FROM knowledge_chunks WHERE source_id = ?"), anyLong());
        verifyNoInteractions(vectors);
    }

    @Test void excludedOrphanVectorsAreRemovedEvenWhenNoSourceRecordRemains() throws Exception {
        excludeReferences();
        when(jdbc.queryForList(contains("SELECT source_path"), eq(String.class))).thenReturn(List.of());
        when(jdbc.update(eq("DELETE FROM knowledge_sources WHERE source_path = ?"), anyString())).thenReturn(0);
        unchangedSource(true);
        assertThat(service.ingestIndexed(false, false)).containsEntry("deletedSources", 0).containsEntry("success", true);
        for (String source : EXCLUDED)
            verify(jdbc).update("DELETE FROM vector_store WHERE metadata->>'source' = ?", source);
        verifyNoInteractions(vectors);
    }

    @Test void missingOrInconsistentVectorsAreRepairedDespiteUnchangedChecksum() throws Exception {
        unchangedSource(false);
        assertThat(service.ingestIndexed(false, false)).containsEntry("indexedSources", 1);
        verify(vectors).index(anyList());
    }

    @Test void excludedPreflightDoesNotDeleteAnything() throws Exception {
        excludeReferences();
        service.ingestIndexed(true, false);
        verify(jdbc, never()).update(anyString(), anyString());
        verify(jdbc, never()).update(anyString());
        verifyNoInteractions(vectors);
    }

    @Test void unrelatedObsoleteSourceStillRequiresExplicitReplacementBeforeSelectiveCleanup() throws Exception {
        excludeReferences();
        when(jdbc.queryForList(contains("SELECT source_path"), eq(String.class)))
                .thenReturn(List.of(EXCLUDED.getFirst(), "unrelated-old.md"));
        assertThatThrownBy(() -> service.ingestIndexed(false, false)).hasMessageContaining("replace-all");
        verify(jdbc, never()).update(anyString(), anyString());
        verifyNoInteractions(vectors);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"vector_store", "knowledge_chunks", "knowledge_sources"})
    void selectiveCleanupFailureRollsBackTheEnclosingTransaction(String table) throws Exception {
        excludeReferences();
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status = mock(org.springframework.transaction.TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(status);
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional = (KnowledgeIngestionService) factory.getProxy();
        doThrow(new IllegalStateException("forced cleanup failure"))
                .when(jdbc).update(startsWith("DELETE FROM " + table + " WHERE"), eq(EXCLUDED.getFirst()));
        assertThatThrownBy(() -> transactional.ingestIndexed(false, false))
                .hasMessage("forced cleanup failure");
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
        verifyNoInteractions(vectors);
    }

    @Test void inconsistentVectorCountsCannotReportSuccessfulSelectiveCleanup() throws Exception {
        excludeReferences();
        unchangedSource(true);
        when(jdbc.queryForObject("SELECT count(*) FROM vector_store", Long.class)).thenReturn(2L);
        assertThatThrownBy(() -> service.ingestIndexed(false, false))
                .hasMessage("Source/chunk/vector counts disagree");
    }

    @Test void lateConsistencyFailureRollsBackEarlierSelectiveDeletes() throws Exception {
        excludeReferences();
        unchangedSource(true);
        when(jdbc.queryForObject(contains("FULL JOIN"), eq(Long.class))).thenReturn(1L);
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status = mock(org.springframework.transaction.TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(status);
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional = (KnowledgeIngestionService) factory.getProxy();
        assertThatThrownBy(() -> transactional.ingestIndexed(false, false))
                .hasMessage("Vector content/metadata mismatch");
        for (String source : EXCLUDED)
            verify(jdbc).update("DELETE FROM knowledge_sources WHERE source_path = ?", source);
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
        verifyNoInteractions(vectors);
    }

}
