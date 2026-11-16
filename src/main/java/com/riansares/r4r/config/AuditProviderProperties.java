package com.riansares.r4r.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("r4r.rag.audit")
public record AuditProviderProperties(List<GenerationProviderProperties.Provider> providers,
        Duration connectTimeout, Duration probeTimeout, Duration totalTimeout) {
    public AuditProviderProperties {
        providers=providers==null ? List.of() : List.copyOf(providers);
        connectTimeout=connectTimeout==null ? Duration.ofMillis(400) : connectTimeout;
        probeTimeout=probeTimeout==null ? Duration.ofMillis(600) : probeTimeout;
        totalTimeout=totalTimeout==null ? Duration.ofSeconds(60) : totalTimeout;
        if (connectTimeout.toMillis()<2 || probeTimeout.toMillis()<2 || totalTimeout.toMillis()<2
                || totalTimeout.compareTo(Duration.ofSeconds(90))>0)
            throw new IllegalArgumentException("Positive audit timeouts and a global budget within 90s required");
    }
}
