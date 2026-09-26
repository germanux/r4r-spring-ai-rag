package com.riansares.r4r.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "r4r.rag.retrieval")
public record RagRetrievalProperties(
        int topK,
        double minScore) {
}
