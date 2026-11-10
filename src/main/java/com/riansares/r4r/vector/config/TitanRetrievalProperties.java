package com.riansares.r4r.vector.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Titan has its own similarity calibration; Nomic's min-score must never be reused. */
@ConfigurationProperties("r4r.rag.vector.titan")
public record TitanRetrievalProperties(boolean enabled, String functionName, String region,
        Duration timeout, Integer maxTopK, Double minimumSimilarity) {
    public TitanRetrievalProperties {
        if (functionName == null) functionName = "r4r-titan-retrieval";
        if (region == null) region = "eu-north-1";
        if (timeout == null) timeout = Duration.ofSeconds(5);
        if (maxTopK == null) maxTopK = 20;
    }
    public void validate() {
        if (!java.util.Set.of("r4r-titan-retrieval",
                "arn:aws:lambda:eu-north-1:124021120482:function:r4r-titan-retrieval").contains(functionName)
                || !"eu-north-1".equals(region)) throw new IllegalArgumentException("Unexpected Titan Lambda target");
        if (timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("Titan timeout must be between 100ms and 30s");
        if (maxTopK < 1 || maxTopK > 20) throw new IllegalArgumentException("Titan max-top-k must be between 1 and 20");
        if (minimumSimilarity == null || !Double.isFinite(minimumSimilarity) || minimumSimilarity < 0 || minimumSimilarity > 1)
            throw new IllegalArgumentException("Set Titan minimum-similarity after evaluating the Titan corpus");
    }
}
