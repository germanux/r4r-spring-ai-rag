package com.riansares.r4r.config;

import com.riansares.r4r.rag.EmbeddingProviderRouter;
import com.riansares.r4r.rag.ObservedEmbeddingModel;
import java.time.Duration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingProperties;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared ingestion/retrieval embedding model, independent of generation and vector storage. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OllamaEmbeddingProperties.class, EmbeddingFailoverProperties.class})
public class OllamaEmbeddingConfiguration {
    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "ollama", matchIfMissing = true)
    OllamaEmbeddingModel ollamaEmbeddingModel(EmbeddingFailoverProperties failover,
            OllamaEmbeddingProperties properties,
            @Value("${r4r.rag.stream.timeout:180s}") Duration streamTimeout) {
        var router = new EmbeddingProviderRouter(failover, properties.getOptions(), streamTimeout);
        return new ObservedEmbeddingModel(OllamaApi.builder().baseUrl(failover.baseUrl()).build(),
                properties.getOptions(), router);
    }
}
