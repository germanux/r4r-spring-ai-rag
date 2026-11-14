package com.riansares.r4r.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IndexedIngestionCliTransactionTest {
    @Test void windowsRunOnlyAfterOriginalCommit() throws Exception { execute(false, false); }
    @Test void checkOnlyNeverStartsWindowSynchronization() throws Exception { execute(true, false); }
    @Test void originalFailureNeverStartsWindowSynchronization() throws Exception { execute(false, true); }

    private void execute(boolean checkOnly, boolean fail) throws Exception {
        var service = mock(KnowledgeIngestionService.class);
        if (fail) when(service.ingestIndexed(false, false)).thenThrow(new IllegalStateException("original failed"));
        else when(service.ingestIndexed(checkOnly, false)).thenReturn(new HashMap<>(Map.of("success", true)));
        var manager = mock(PlatformTransactionManager.class);
        var commits = new AtomicInteger();
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        doAnswer(call -> { commits.incrementAndGet(); return null; }).when(manager).commit(any());
        var factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var context = mock(ConfigurableApplicationContext.class);
        when(context.getEnvironment()).thenReturn(new MockEnvironment()
                .withProperty("r4r.ingestion.index", "true")
                .withProperty("r4r.ingestion.check-only", Boolean.toString(checkOnly)));
        when(context.getBean(KnowledgeIngestionService.class)).thenReturn((KnowledgeIngestionService) factory.getProxy());
        when(context.getBean(PlatformTransactionManager.class)).thenReturn(manager);
        var jdbc = mock(JdbcTemplate.class); var embeddings = mock(EmbeddingModel.class);
        when(context.getBean(JdbcTemplate.class)).thenReturn(jdbc);
        when(context.getBean(EmbeddingModel.class)).thenReturn(embeddings);
        var builder = mock(SpringApplicationBuilder.class, RETURNS_SELF);
        when(builder.run(any(String[].class))).thenReturn(context);
        try (var cli = mockStatic(KnowledgeIngestionCli.class, CALLS_REAL_METHODS);
             var windows = mockStatic(ExperimentalTripleWindowIndexer.class)) {
            cli.when(KnowledgeIngestionCli::createBuilder).thenReturn(builder);
            windows.when(() -> ExperimentalTripleWindowIndexer.synchronize(jdbc, embeddings)).thenAnswer(call -> {
                assertThat(commits.get()).as("original transaction already committed").isEqualTo(1);
                return Map.of("total", 695);
            });
            assertThat(KnowledgeIngestionCli.execute(new String[0])).isEqualTo(fail ? 4 : 0);
            if (fail || checkOnly) windows.verifyNoInteractions();
            else windows.verify(() -> ExperimentalTripleWindowIndexer.synchronize(jdbc, embeddings));
            assertThat(commits.get()).isEqualTo(fail ? 0 : checkOnly ? 1 : 2);
            verify(context).close();
        }
    }
}
