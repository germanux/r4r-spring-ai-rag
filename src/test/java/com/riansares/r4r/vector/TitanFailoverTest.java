package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.rag.EmbeddingUnavailableException;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import software.amazon.awssdk.core.exception.SdkClientException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TitanFailoverTest {
    final KnowledgeVectorStore pg = mock(KnowledgeVectorStore.class), s3 = mock(KnowledgeVectorStore.class), titan = mock(KnowledgeVectorStore.class);
    final List<MarkdownChunk> docs = List.of(new MarkdownChunk("faq.md", List.of("FAQ"), 2, "Evidence"));
    FailoverKnowledgeVectorStore router(boolean enabled) { return new FailoverKnowledgeVectorStore(() -> pg, s3, "pgvector", true, false, null, enabled ? titan : null); }
    void pgDown() { doThrow(new CannotGetJdbcConnectionException("offline")).when(pg).search("q", 5, .6); }
    @Test void enabledTitanDoesNotChangeWriteOwnership() {
        var router=router(true);router.index(docs);router.replaceSource("faq.md",docs);
        verify(pg).index(docs);verify(pg).replaceSource("faq.md",docs);verifyNoInteractions(s3,titan);
    }
    @Test void healthyPgAndEmptyPgDoNotCallAnySecondary() {
        when(pg.search("q", 5, .6)).thenReturn(docs, List.of());
        assertThat(router(true).search("q", 5, .6)).isSameAs(docs);
        assertThat(router(true).search("q", 5, .6)).isEmpty(); verifyNoInteractions(s3, titan);
    }
    @Test void healthyAndEmptyNomicDoNotCallTitan() {
        pgDown(); when(s3.search("q", 5, .6)).thenReturn(docs, List.of());
        assertThat(router(true).search("q", 5, .6)).isSameAs(docs);
        assertThat(router(true).search("q", 5, .6)).isEmpty(); verifyNoInteractions(titan);
    }
    @Test void tertiaryRunsOnlyAfterBothFailuresAndPgRecoveryRestoresPriority() {
        doThrow(new CannotGetJdbcConnectionException("offline")).doReturn(docs).when(pg).search("q", 5, .6);
        when(s3.search("q", 5, .6)).thenThrow(SdkClientException.create("offline")); when(titan.search("q", 5, .6)).thenReturn(docs);
        var router = router(true); assertThat(router.search("q", 5, .6)).isSameAs(docs);
        var order = inOrder(pg, s3, titan); order.verify(pg).search("q", 5, .6); order.verify(s3).search("q", 5, .6); order.verify(titan).search("q", 5, .6);
        assertThat(router.search("q", 5, .6)).isSameAs(docs); verify(titan, times(1)).search("q", 5, .6);
    }
    @Test void disabledTitanRetainsControlledBothUnavailableResponse() {
        pgDown(); when(s3.search("q", 5, .6)).thenThrow(SdkClientException.create("offline"));
        assertThatThrownBy(() -> router(false).search("q", 5, .6)).isInstanceOf(VectorStoreUnavailableException.class); verifyNoInteractions(titan);
    }
    @Test void nomicOutageCanUseIndependentTitanWithoutRepeatingNomic() {
        when(pg.search("q", 5, .6)).thenThrow(new EmbeddingUnavailableException("offline"));
        when(titan.search("q", 5, .6)).thenReturn(docs);
        assertThat(router(true).search("q", 5, .6)).isSameAs(docs); verifyNoInteractions(s3);
    }
    @Test void allUnavailableAndEmptyTitanRemainDistinct() {
        pgDown(); when(s3.search("q", 5, .6)).thenThrow(SdkClientException.create("offline"));
        when(titan.search("q", 5, .6)).thenThrow(new VectorStoreUnavailableException("offline", null)).thenReturn(List.of());
        assertThatThrownBy(() -> router(true).search("q", 5, .6)).isInstanceOf(VectorStoreUnavailableException.class);
        assertThat(router(true).search("q", 5, .6)).isEmpty();
    }
    @Test void s3ValidationFailuresAndCancellationNeverBypassToTitan() {
        for (RuntimeException failure : List.of(new IllegalStateException("Invalid metadata"), new CancellationException())) {
            pgDown(); doThrow(failure).when(s3).search("q", 5, .6);
            assertThatThrownBy(() -> router(true).search("q", 5, .6)).isInstanceOf(RuntimeException.class); verifyNoInteractions(titan);
        }
    }
}
