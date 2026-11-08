package com.riansares.r4r.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Independent embedding endpoints; generation configuration is deliberately separate. */
@ConfigurationProperties("r4r.rag.embedding")
public record EmbeddingFailoverProperties(String baseUrl, String pcBaseUrl, String nasBaseUrl,
        Duration connectTimeout, Duration readTimeout, Duration pcReadTimeout, Duration nasReadTimeout,
        Duration probeTimeout, Duration totalTimeout, int dimensions, String expectedDigest) { }
