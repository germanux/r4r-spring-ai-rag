package com.riansares.r4r.config;

import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingProperties;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
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
            OllamaEmbeddingProperties properties) {
        return OllamaEmbeddingModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build())
                .defaultOptions(properties.getOptions())
                .build();
    }
}
