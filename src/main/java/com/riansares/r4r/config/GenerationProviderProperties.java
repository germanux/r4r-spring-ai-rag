package com.riansares.r4r.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Ordered local/remote providers. URL, model and read timeout always travel together. */
@ConfigurationProperties("r4r.rag.generation")
public record GenerationProviderProperties(List<Provider> providers, Duration connectTimeout,
        Duration probeTimeout) {
    public GenerationProviderProperties {
        if (providers == null || providers.isEmpty()) throw new IllegalArgumentException("At least one generation provider required");
        providers = List.copyOf(providers);
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        probeTimeout = probeTimeout == null ? Duration.ofSeconds(2) : probeTimeout;
        if (connectTimeout.toMillis() < 2 || probeTimeout.toMillis() < 2)
            throw new IllegalArgumentException("Positive generation connection and probe timeouts required");
    }
    public record Provider(String name, String baseUrl, String model, Duration readTimeout) {
        public boolean remote() { return "OpenAI".equals(name); }
    }
}
