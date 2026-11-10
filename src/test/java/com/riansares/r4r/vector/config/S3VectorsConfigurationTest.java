package com.riansares.r4r.vector.config;

import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No database, AWS requests, credential files, or LLMs. */
class S3VectorsConfigurationTest {
    private final EmbeddingModel embeddings = mock(EmbeddingModel.class);
    private final S3VectorsClient client = mock(S3VectorsClient.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KnowledgeVectorStoreConfiguration.class, S3VectorsConfiguration.class, ConsumerConfiguration.class)
            .withBean(VectorStore.class, () -> mock(VectorStore.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(EmbeddingModel.class, () -> embeddings);

    @Configuration(proxyBeanMethods = false)
    static class ConsumerConfiguration {
        @Bean DefaultConsumer defaultConsumer(KnowledgeVectorStore store) { return new DefaultConsumer(store); }
    }
    record DefaultConsumer(KnowledgeVectorStore store) { }

    @Configuration(proxyBeanMethods = false)
    static class SecondaryConsumerConfiguration {
        @Bean SecondaryConsumer secondaryConsumer(@Qualifier("s3VectorsKnowledgeVectorStore") KnowledgeVectorStore store) {
            return new SecondaryConsumer(store);
        }
    }
    record SecondaryConsumer(KnowledgeVectorStore store) { }

    @Test void lambdaTransportCreatesOnlyLambdaClient() {
        runner.withPropertyValues("r4r.rag.vector.s3vectors.enabled=true", "r4r.rag.vector.s3vectors.transport=lambda",
            "r4r.rag.vector.s3vectors.bucket=amzn-r4r-vectors", "r4r.rag.vector.s3vectors.index=amzn-r4r-vector-index")
            .run(context -> assertThat(context).hasNotFailed().hasSingleBean(software.amazon.awssdk.services.lambda.LambdaClient.class)
                .doesNotHaveBean(S3VectorsClient.class));
    }

    @Test void defaultPgDoesNotCreateClientResolveCredentialsOrRequireS3Configuration() {
        try (var credentials = mockStatic(DefaultCredentialsProvider.class); var aws = mockStatic(S3VectorsClient.class)) {
            runner.withPropertyValues("r4r.rag.vector.s3vectors.bucket=INVALID")
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(com.riansares.r4r.vector.FailoverKnowledgeVectorStore.class)
                                .doesNotHaveBean(S3VectorsClient.class).hasSingleBean(RagVectorProperties.class);
                        assertThat(context.getBean(DefaultConsumer.class).store()).isSameAs(context.getBean("knowledgeVectorStore"));
                    });
            credentials.verifyNoInteractions(); aws.verifyNoInteractions();
        }
        verifyNoInteractions(embeddings, client);
    }

    @Test void enabledSecondaryRequiresExplicitQualifierAndLeavesDefaultPgInjectionUnchanged() {
        enabled().withUserConfiguration(SecondaryConsumerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(KnowledgeVectorStore.class)).hasSize(3);
                    var primary = context.getBean(DefaultConsumer.class).store();
                    var secondary = context.getBean(SecondaryConsumer.class).store();
                    assertThat(primary).isSameAs(context.getBean("knowledgeVectorStore"));
                    assertThat(secondary).isSameAs(context.getBean("s3VectorsKnowledgeVectorStore"));
                    assertThat(secondary).isNotSameAs(primary);
                    assertThatThrownBy(() -> secondary.index(java.util.List.of())).isInstanceOf(UnsupportedOperationException.class);
                    var properties = context.getBean(RagVectorProperties.class);
                    assertThat(properties.primary()).isEqualTo("pgvector");
                    assertThat(properties.failoverEnabled()).isFalse();
                    assertThat(properties.s3vectors().region()).isEqualTo("eu-north-1");
                    assertThat(properties.s3vectors().apiCallTimeout()).isEqualTo(java.time.Duration.ofSeconds(4));
                });
        verify(client).close();
        verifyNoMoreInteractions(client);
        verifyNoInteractions(embeddings);
    }

    @Test void invalidNamesRegionAndTimeoutsFailLocallyBeforeUsingInfrastructure() {
        for (String property : new String[]{"bucket=", "index=INVALID", "region=bad-region", "connect-timeout=0s",
                "api-call-attempt-timeout=-1s", "api-call-timeout=3m", "connect-timeout=4s", "api-call-timeout=1s"}) {
            enabled().withPropertyValues("r4r.rag.vector.s3vectors." + property).run(context ->
                    assertThat(context).hasFailed().getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class));
        }
        verify(client, times(8)).close();
        verifyNoMoreInteractions(client);
        verifyNoInteractions(embeddings);
    }

    @Test void disabledExplicitlyIgnoresInvalidConfiguration() {
        enabled().withPropertyValues("r4r.rag.vector.s3vectors.enabled=false", "r4r.rag.vector.s3vectors.region=INVALID")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("s3VectorsKnowledgeVectorStore"));
        verify(client).close();
        verifyNoMoreInteractions(client);
        verifyNoInteractions(embeddings);
    }

    @Test void actualSdkFactoryBuildsOfflineWithExplicitTimeoutsWithoutResolvingCredentials() {
        var credentials = mock(DefaultCredentialsProvider.class);
        var builder = mock(DefaultCredentialsProvider.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(credentials);
        try (var defaults = mockStatic(DefaultCredentialsProvider.class)) {
            defaults.when(DefaultCredentialsProvider::builder).thenReturn(builder);
            new ApplicationContextRunner().withUserConfiguration(S3VectorsConfiguration.class)
                    .withBean(EmbeddingModel.class, () -> embeddings)
                    .withPropertyValues("r4r.rag.vector.s3vectors.enabled=true",
                            "r4r.rag.vector.s3vectors.bucket=test-vector-bucket",
                            "r4r.rag.vector.s3vectors.index=test-vector-index")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var config = context.getBean(S3VectorsClient.class).serviceClientConfiguration();
                        assertThat(config.region().id()).isEqualTo("eu-north-1");
                        assertThat(config.overrideConfiguration().apiCallTimeout()).contains(Duration.ofSeconds(4));
                        assertThat(config.overrideConfiguration().apiCallAttemptTimeout()).contains(Duration.ofSeconds(3));
                        verifyNoInteractions(credentials, embeddings);
                    });
            defaults.verify(DefaultCredentialsProvider::builder);
        }
        // Only resource cleanup is allowed; credentials were never requested.
        verify(credentials, atLeastOnce()).close();
        verifyNoMoreInteractions(credentials);
    }

    private ApplicationContextRunner enabled() {
        return runner.withPropertyValues("r4r.rag.vector.s3vectors.enabled=true",
                        "r4r.rag.vector.s3vectors.bucket=test-vector-bucket", "r4r.rag.vector.s3vectors.index=test-vector-index")
                .withBean("s3VectorsClient", S3VectorsClient.class, () -> client);
    }
}
