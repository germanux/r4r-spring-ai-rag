package com.riansares.r4r.vector.config;

import com.riansares.r4r.vector.titan.TitanLambdaKnowledgeStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.lambda.LambdaClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "r4r.rag.vector.titan", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TitanRetrievalProperties.class)
public class TitanRetrievalConfiguration {
    @Bean(name = "titanLambdaClient", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "titanLambdaClient")
    LambdaClient titanLambdaClient(TitanRetrievalProperties properties) {
        properties.validate();
        return LambdaClient.builder().region(Region.of(properties.region()))
                // Production identity comes from the same SDK default chain as S3/Nomic. No deployer profile.
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(java.time.Duration.ofSeconds(1))
                        .socketTimeout(properties.timeout()))
                .overrideConfiguration(c -> c.apiCallTimeout(properties.timeout()).apiCallAttemptTimeout(properties.timeout())
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())).build();
    }
    @Bean(name = "titanKnowledgeVectorStore", defaultCandidate = false)
    TitanLambdaKnowledgeStore titanKnowledgeVectorStore(@Qualifier("titanLambdaClient") LambdaClient client,
            TitanRetrievalProperties properties) {
        return new TitanLambdaKnowledgeStore(client, properties);
    }
}
