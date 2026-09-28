package com.riansares.r4r.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "r4r.api-security")
public record RagApiSecurityProperties(
        boolean enabled,
        List<String> allowedOrigins,
        Duration minInterval,
        boolean requireOrigin,
        boolean trustProxyHeaders,
        int maxTrackedIps) {

    public RagApiSecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        minInterval = minInterval == null || minInterval.isNegative() ? Duration.ZERO : minInterval;
        maxTrackedIps = maxTrackedIps <= 0 ? 10_000 : maxTrackedIps;
    }
}
