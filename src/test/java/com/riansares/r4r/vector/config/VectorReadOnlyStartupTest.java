package com.riansares.r4r.vector.config;

import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import com.riansares.r4r.vector.VectorStoreUnavailableException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring auto-configuration with unavailable mocked DB and mocked AWS/embeddings. */
class VectorReadOnlyStartupTest {
    @Test void recoveryProfileStartsAndServesS3WithoutAnyDatabaseConnection() throws Exception {
        var db=mock(DataSource.class); when(db.getConnection()).thenThrow(new java.sql.SQLException("offline","08006"));
        var embedding=mock(EmbeddingModel.class); when(embedding.dimensions()).thenReturn(768);
        float[] vector=new float[768]; vector[0]=1;
        when(embedding.embed("question")).thenReturn(vector);
        var aws=mock(S3VectorsClient.class);
        when(aws.queryVectors(any(QueryVectorsRequest.class))).thenReturn(QueryVectorsResponse.builder().distanceMetric(DistanceMetric.COSINE).build());
        new ApplicationContextRunner()
                .withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles("s3-readonly");
                    try {
                        for (var source:new YamlPropertySourceLoader().load("recovery",new ClassPathResource("application-s3-readonly.yml")))
                            ctx.getEnvironment().getPropertySources().addLast(source);
                    } catch (Exception e) { throw new IllegalStateException(e); }
                })
                .withConfiguration(AutoConfigurations.of(JdbcTemplateAutoConfiguration.class,
                        PgVectorStoreAutoConfiguration.class,FlywayAutoConfiguration.class))
                .withUserConfiguration(KnowledgeVectorStoreConfiguration.class,S3VectorsConfiguration.class,S3ReadOnlyStartupConfiguration.class)
                .withBean(DataSource.class,() -> db).withBean(EmbeddingModel.class,() -> embedding)
                .withBean("s3VectorsClient",S3VectorsClient.class,() -> aws)
                .withPropertyValues("r4r.rag.vector.s3vectors.bucket=amzn-r4r-vectors",
                        "r4r.rag.vector.s3vectors.index=amzn-r4r-vector-index",
                        "spring.ai.vectorstore.pgvector.dimensions=768")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().doesNotHaveBean(org.flywaydb.core.Flyway.class);
                    assertThat(ctx.getBeanFactory().containsSingleton("vectorStore")).isFalse();
                    var port=ctx.getBean(KnowledgeVectorStore.class);
                    assertThat(port.search("question",5,.63)).isEmpty();
                    assertThatThrownBy(() -> port.index(java.util.List.of())).isInstanceOf(VectorStoreUnavailableException.class);
                    assertThat(ctx.getBeanFactory().containsSingleton("pgVectorKnowledgeStore")).isFalse();
                });
        verify(db,never()).getConnection();
        verify(aws).queryVectors(argThat((QueryVectorsRequest request) -> request.vectorBucketName().equals("amzn-r4r-vectors")
                && request.indexName().equals("amzn-r4r-vector-index")));
    }

    @Test void invalidCombinationsFailBeforeUsingStorage() {
        for (String[] flags:new String[][] {
                {"unknown","false","false"}, {"pgvector","true","false"},
                {"s3vectors","false","false"}, {"s3vectors","true","true"}}) {
            var runner=new ApplicationContextRunner().withUserConfiguration(KnowledgeVectorStoreConfiguration.class)
                    .withPropertyValues("r4r.rag.vector.primary="+flags[0],"r4r.rag.vector.failover-enabled="+flags[1],
                            "r4r.rag.vector.s3vectors.enabled="+flags[2]);
            if (flags[2].equals("true")) runner=runner.withBean("s3VectorsKnowledgeVectorStore",KnowledgeVectorStore.class,
                    () -> mock(KnowledgeVectorStore.class));
            runner.run(ctx -> assertThat(ctx).hasFailed());
        }
    }
    @Test void normalConfigurationKeepsMigrationsAndSchemaValidation() throws Exception {
        var sources=new YamlPropertySourceLoader().load("normal",new ClassPathResource("application.yml"));
        // YAML-only inspection: do not resolve its runtime imports or open private configuration.
        assertThat(sources.getFirst().getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(sources.getFirst().getProperty("spring.ai.vectorstore.pgvector.schema-validation")).isEqualTo(true);
    }
}
