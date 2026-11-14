package com.riansares.r4r.vector.config;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the production bean through its port without a database, AWS or live LLM. */
class KnowledgeVectorStoreConfigurationTest {
    private final VectorStore vectors = mock(VectorStore.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KnowledgeVectorStoreConfiguration.class, TransactionConfiguration.class)
            .withBean(VectorStore.class, () -> vectors)
            .withBean(JdbcTemplate.class, () -> jdbc)
            .withBean(PlatformTransactionManager.class, () -> transactions);

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfiguration {
        @org.springframework.context.annotation.Bean
        InjectionProbe injectionProbe(KnowledgeVectorStore port, PgVectorKnowledgeStore adapter) {
            return new InjectionProbe(port, adapter);
        }
    }
    record InjectionProbe(KnowledgeVectorStore port, PgVectorKnowledgeStore adapter) { }

    @Test
    void defaultConfigurationExposesOnePostgresPortWithoutAwsProperties() {
        runner.run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(com.riansares.r4r.vector.FailoverKnowledgeVectorStore.class)
                    .hasSingleBean(PgVectorKnowledgeStore.class);
            KnowledgeVectorStore store = context.getBean(KnowledgeVectorStore.class);
            assertThat(store).isInstanceOf(com.riansares.r4r.vector.FailoverKnowledgeVectorStore.class);
            assertThat(context.getBean(InjectionProbe.class).port()).isSameAs(store);
            assertThat(context.getBean(InjectionProbe.class).adapter()).isSameAs(context.getBean(PgVectorKnowledgeStore.class));
            assertThat(AopUtils.isAopProxy(context.getBean(PgVectorKnowledgeStore.class))).isTrue();
            verify(jdbc).afterPropertiesSet();
            verifyNoMoreInteractions(jdbc);
            verifyNoInteractions(vectors, transactions);
        });
    }

    @Test
    void portSearchPreservesRequestAndRetrievalOrder() {
        var first = chunk("one.md", 2, "First evidence.");
        var second = chunk("two.md", 4, "Second evidence.");
        when(vectors.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(document(first), document(second)));

        runner.run(context -> {
            var store = context.getBean(KnowledgeVectorStore.class);
            assertThat(store.search("consulta", 5, .63)).containsExactly(first, second);
            verify(vectors).similaritySearch(argThat((SearchRequest request) ->
                    request.getQuery().equals(com.riansares.r4r.rag.ObservedEmbeddingModel.retrievalQuery("consulta"))
                            && request.getTopK() == 20
                            && request.getSimilarityThreshold() == .63));
            verify(jdbc).afterPropertiesSet();
            verifyNoMoreInteractions(jdbc);
            verifyNoInteractions(transactions);
        });
    }

    @Test
    void explicitLegacyRankingKeepsOriginalRequestAndThreshold() {
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        runner.withPropertyValues("r4r.rag.retrieval.rerank.candidate-multiplier=1",
                "r4r.rag.retrieval.rerank.lexical-weight=0").run(context -> {
            assertThat(context.getBean(KnowledgeVectorStore.class).search("q",5,.63)).isEmpty();
            verify(vectors).similaritySearch(argThat((SearchRequest request) -> request.getTopK()==5
                    && request.getSimilarityThreshold()==.63));
        });
    }

    @Test
    void indexCommitsThroughTransactionalPort() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        runner.run(context -> {
            context.getBean(KnowledgeVectorStore.class).index(List.of(chunk("one.md", 0, "Evidence.")));
            var order = inOrder(transactions, jdbc, vectors);
            order.verify(transactions).getTransaction(any());
            order.verify(jdbc).update("DELETE FROM vector_store WHERE metadata->>'source' = ?", "one.md");
            order.verify(vectors).add(anyList());
            order.verify(transactions).commit(any());
            verify(transactions, never()).rollback(any());
        });
    }

    @Test
    void replacementFailureRollsBackThroughTransactionalPort() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var failure = new IllegalStateException("simulated insertion failure");
        doThrow(failure).when(vectors).add(anyList());
        runner.run(context -> {
            var store = context.getBean(KnowledgeVectorStore.class);
            assertThatThrownBy(() -> store.replaceSource("one.md", List.of(chunk("one.md", 0, "New evidence."))))
                    .isSameAs(failure);
            var order = inOrder(transactions, jdbc, vectors);
            order.verify(transactions).getTransaction(any());
            order.verify(jdbc).update("DELETE FROM vector_store WHERE metadata->>'source' = ?", "one.md");
            order.verify(vectors).add(anyList());
            order.verify(transactions).rollback(any());
            verify(transactions, never()).commit(any());
        });
    }

    private static MarkdownChunk chunk(String source, int ordinal, String content) {
        return new MarkdownChunk(source, List.of("Section"), ordinal, content);
    }

    private static Document document(MarkdownChunk chunk) {
        return Document.builder().text(chunk.content()).metadata(Map.of(
                "source", chunk.source(), "headingPath", chunk.headingPath(), "ordinal", chunk.index())).build();
    }
}
