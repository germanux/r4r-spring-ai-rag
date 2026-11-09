package com.riansares.r4r.vector.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("r4r.rag.vector.postgres")
public record PostgresFailoverProperties(@DefaultValue("30s") Duration recoveryInterval,
        @DefaultValue("3s") Duration connectionTimeout, @DefaultValue("5s") Duration socketTimeout) {
    public PostgresFailoverProperties {
        require(recoveryInterval, Duration.ofSeconds(1), Duration.ofMinutes(5));
        require(connectionTimeout, Duration.ofMillis(500), Duration.ofSeconds(30));
        require(socketTimeout, Duration.ofSeconds(1), Duration.ofSeconds(60));
    }
    private static void require(Duration value, Duration minimum, Duration maximum) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0)
            throw new IllegalArgumentException("Invalid PostgreSQL failover timeout");
    }
}
