package com.riansares.r4r.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Bounded second-stage Nomic ranking; raw cosine threshold remains unchanged. */
@ConfigurationProperties("r4r.rag.retrieval.rerank")
public record NomicRerankProperties(@DefaultValue("4") int candidateMultiplier,
        @DefaultValue("0.075") double lexicalWeight) {
    public NomicRerankProperties {
        if (candidateMultiplier < 1 || candidateMultiplier > 10)
            throw new IllegalArgumentException("Nomic candidate-multiplier must be between 1 and 10");
        if (!Double.isFinite(lexicalWeight) || lexicalWeight < 0 || lexicalWeight > .25)
            throw new IllegalArgumentException("Nomic lexical-weight must be finite and between 0 and 0.25");
    }
    public static NomicRerankProperties defaults() { return new NomicRerankProperties(4, .075); }
}
