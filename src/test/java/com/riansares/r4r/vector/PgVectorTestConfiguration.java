package com.riansares.r4r.vector;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class PgVectorTestConfiguration {

    @Bean
    @Primary
    EmbeddingModel deterministicEmbeddingModel() {
        return new DeterministicEmbeddingModel();
    }
}
