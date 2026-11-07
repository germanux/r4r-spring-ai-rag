package com.riansares.r4r.config;

import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingProperties;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import com.riansares.r4r.rag.ObservedEmbeddingModel;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One embedding model for both ingestion and retrieval, isolated from generation URLs. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OllamaEmbeddingProperties.class)
public class OllamaEmbeddingConfiguration {
    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "ollama", matchIfMissing = true)
    OllamaEmbeddingModel ollamaEmbeddingModel(
            @Value("${r4r.rag.embedding.base-url}") String baseUrl,
            OllamaEmbeddingProperties properties,
            @Value("${r4r.rag.embedding.connect-timeout:2s}") Duration connectTimeout,
            @Value("${r4r.rag.embedding.read-timeout:30s}") Duration readTimeout) {
        var http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(connectTimeout);
        http.setReadTimeout(readTimeout);
        return new ObservedEmbeddingModel(OllamaApi.builder().baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder().requestFactory(http)).build(), properties.getOptions());
    }
}
