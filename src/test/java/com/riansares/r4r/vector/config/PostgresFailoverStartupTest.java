package com.riansares.r4r.vector.config;

import com.riansares.r4r.vector.*;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import com.riansares.r4r.ingestion.KnowledgeIngestionService;
import com.riansares.r4r.document.MarkdownDocumentLoader;
import com.riansares.r4r.chunking.HeadingMarkdownChunker;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostgresFailoverStartupTest {
    final DataSource db = mock(DataSource.class);
    final EmbeddingModel embeddings = mock(EmbeddingModel.class);
    final S3VectorsClient aws = mock(S3VectorsClient.class);
    ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JdbcTemplateAutoConfiguration.class,
                        FlywayAutoConfiguration.class, PgVectorStoreAutoConfiguration.class))
                .withUserConfiguration(PostgresFailoverConfiguration.class, KnowledgeVectorStoreConfiguration.class, S3VectorsConfiguration.class)
                .withBean(DataSource.class, () -> db).withBean(EmbeddingModel.class, () -> embeddings)
                .withBean("s3VectorsClient", S3VectorsClient.class, () -> aws)
                .withPropertyValues("r4r.rag.vector.primary=pgvector", "r4r.rag.vector.failover-enabled=true",
                        "r4r.rag.vector.s3vectors.enabled=true", "r4r.rag.vector.s3vectors.bucket=amzn-r4r-vectors",
                        "r4r.rag.vector.s3vectors.index=amzn-r4r-vector-index", "spring.ai.vectorstore.pgvector.dimensions=768",
                        "spring.ai.vectorstore.pgvector.initialize-schema=false", "spring.sql.init.mode=never");
    }
    @Test void actualFlywayInitializationDefersUnknownHostAndActualPgVectorBeanStaysLazy() throws Exception {
        when(db.getConnection()).thenThrow(new SQLException("unreachable", "08001", new java.net.UnknownHostException("postgres")));
        float[] vector = new float[768]; vector[0] = 1;
        when(embeddings.embed("question")).thenReturn(vector);
        when(aws.queryVectors(any(QueryVectorsRequest.class))).thenReturn(QueryVectorsResponse.builder().distanceMetric(DistanceMetric.COSINE).build());
        context().run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(Flyway.class).hasSingleBean(PostgresAccessGate.class);
            assertThat(ctx.getBeanFactory().containsSingleton("vectorStore")).isFalse();
            assertThat(ctx.getBeanFactory().containsSingleton("pgVectorKnowledgeStore")).isFalse();
            assertThat(ctx.getBean(KnowledgeVectorStore.class).search("question", 5, .63)).isEmpty();
            assertThatThrownBy(() -> ctx.getBean(KnowledgeVectorStore.class).index(List.of())).isInstanceOf(org.springframework.jdbc.CannotGetJdbcConnectionException.class);
            verify(db).getConnection(); // No connection storm from repeated reads/writes.
        });
        verify(aws).queryVectors(argThat((QueryVectorsRequest r) -> r.queryVector().float32().size() == 768 && r.returnMetadata()));
    }
    @Test void disabledAwsRetainsFatalTraditionalStartup() throws Exception {
        when(db.getConnection()).thenThrow(new SQLException("unreachable", "08001"));
        context().withPropertyValues("r4r.rag.vector.failover-enabled=false", "r4r.rag.vector.s3vectors.enabled=false")
                .run(ctx -> assertThat(ctx).hasFailed());
        verify(aws, never()).queryVectors(any(QueryVectorsRequest.class));
    }
    @Test void authenticationAndInvalidSchemaAreNeverTreatedAsDegradedStartup() throws Exception {
        for (String state : List.of("28P01", "42P01")) {
            reset(db); when(db.getConnection()).thenThrow(new SQLException("synthetic failure", state));
            context().run(ctx -> assertThat(ctx).hasFailed());
        }
        verify(aws, never()).queryVectors(any(QueryVectorsRequest.class));
    }
    @Test void hikariIsBoundedOnlyWhenFailoverIsEnabled() {
        var pool = new com.zaxxer.hikari.HikariDataSource();
        context().withBean("poolSettingsProbe", com.zaxxer.hikari.HikariDataSource.class, () -> pool)
                .withPropertyValues("spring.flyway.enabled=false").run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(pool.getConnectionTimeout()).isEqualTo(3000);
                    assertThat(pool.getValidationTimeout()).isEqualTo(1000);
                    assertThat(pool.getInitializationFailTimeout()).isEqualTo(-1);
                    assertThat(pool.getMinimumIdle()).isZero();
                    assertThat(pool.getDataSourceProperties().get("connectTimeout").toString()).isEqualTo("3");
                    assertThat(pool.getDataSourceProperties().get("socketTimeout").toString()).isEqualTo("5");
                });
    }
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true)
    static class Writers {
        @Bean KnowledgeIngestionService ingestion(JdbcTemplate jdbc) {
            return new KnowledgeIngestionService(jdbc, mock(MarkdownDocumentLoader.class), mock(HeadingMarkdownChunker.class));
        }
    }
    @Test void bothWriteEntryPointsAreGuardedBeforeTheirTransactionStarts() throws Exception {
        when(db.getConnection()).thenReturn(mock(Connection.class));
        var transactions = mock(PlatformTransactionManager.class);
        var vectors = mock(org.springframework.ai.vectorstore.pgvector.PgVectorStore.class);
        context().withPropertyValues("spring.flyway.enabled=false")
                .withUserConfiguration(Writers.class).withBean("vectorStore", org.springframework.ai.vectorstore.pgvector.PgVectorStore.class, () -> vectors)
                .withBean(PlatformTransactionManager.class, () -> transactions).run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    // No Flyway validation: neither concrete injection nor the port can start a write transaction.
                    assertThatThrownBy(() -> ctx.getBean(PgVectorKnowledgeStore.class).index(List.of()))
                            .isInstanceOf(VectorStoreUnavailableException.class);
                    assertThatThrownBy(() -> ctx.getBean(KnowledgeIngestionService.class).ingest())
                            .isInstanceOf(VectorStoreUnavailableException.class);
                    verifyNoInteractions(transactions, aws);
                    verify(vectors, never()).add(anyList());
                    verify(vectors, never()).delete(anyList());
                });
    }
}
