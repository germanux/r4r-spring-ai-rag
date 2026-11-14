package com.riansares.r4r.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Ordered local providers. URL, model and read timeout always travel together. */
@ConfigurationProperties("r4r.rag.generation")
public record GenerationProviderProperties(List<Provider> providers, Duration connectTimeout,
        Duration probeTimeout) {
    public record Provider(String name, String baseUrl, String model, Duration readTimeout) { }
}
