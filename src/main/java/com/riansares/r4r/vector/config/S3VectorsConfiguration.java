package com.riansares.r4r.vector.config;

import com.riansares.r4r.config.NomicRerankProperties;
import com.riansares.r4r.vector.s3vectors.S3VectorMetadataMapper;
import com.riansares.r4r.vector.s3vectors.S3VectorScoreMapper;
import com.riansares.r4r.vector.s3vectors.S3VectorsKnowledgeStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

/** Creates the independent S3 adapter; KnowledgeVectorStoreConfiguration owns routing. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "r4r.rag.vector.s3vectors", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({RagVectorProperties.class, NomicRerankProperties.class})
public class S3VectorsConfiguration {

    @Bean(name = "s3VectorsClient", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "s3VectorsClient")
    S3VectorsClient s3VectorsClient(RagVectorProperties properties) {
        var config = properties.s3vectors();
        config.validate();
        return S3VectorsClient.builder()
                .region(Region.of(config.region()))
                // SDK default chain resolves AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY from the container environment.
                // Region is explicit above; no profile, credential file or Spring-to-SDK credential adapter is needed.
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(config.connectTimeout())
                        .socketTimeout(config.apiCallAttemptTimeout()))
                .overrideConfiguration(c -> c.apiCallAttemptTimeout(config.apiCallAttemptTimeout())
                        .apiCallTimeout(config.apiCallTimeout())
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()))
                .build();
    }

    // Explicit qualifier required: an enabled secondary must not compete with the default PG bean.
    @Bean(name = "s3VectorsKnowledgeVectorStore", defaultCandidate = false)
    S3VectorsKnowledgeStore s3VectorsKnowledgeVectorStore(
            @Qualifier("s3VectorsClient") S3VectorsClient client,
            EmbeddingModel embeddingModel, RagVectorProperties properties, NomicRerankProperties ranking) {
        return new S3VectorsKnowledgeStore(client, embeddingModel, properties.s3vectors(),
                new S3VectorMetadataMapper(), new S3VectorScoreMapper(), ranking);
    }
}
