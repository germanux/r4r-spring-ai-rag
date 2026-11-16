package com.riansares.r4r.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** One ordered provider list, shared by embedding routing and primary-model preparation. */
@ConfigurationProperties("r4r.rag.embedding")
public record EmbeddingFailoverProperties(List<Provider> providers, Duration connectTimeout,
        Duration probeTimeout, Duration totalTimeout, int dimensions, String expectedDigest) {
    public record Provider(String name, String baseUrl, String model, Duration readTimeout) {
        public Provider(String name, String baseUrl, Duration readTimeout) {
            this(name,baseUrl,"qwen3-embedding:0.6b",readTimeout);
        }
        @org.springframework.boot.context.properties.bind.ConstructorBinding
        public Provider { if (model==null) model="qwen3-embedding:0.6b"; }
    }
}
