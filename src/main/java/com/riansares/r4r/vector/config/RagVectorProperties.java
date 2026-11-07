package com.riansares.r4r.vector.config;

import java.time.Duration;

/**
 * Passive phase 3 configuration contract for r4r.rag.vector; not bound or registered.
 * No defaults, validation or provider selection are implemented.
 * Intended defaults: primary=pgvector, failoverEnabled=false.
 * Existing AWS resources reported by the user: eu-north-1 (verify before access),
 * amzn-r4r-vectors / amzn-r4r-vector-index, 768 dimensions, COSINE, SSE-S3.
 * Credentials are deliberately excluded from this contract.
 */
public record RagVectorProperties(
        String primary,
        boolean failoverEnabled,
        S3Vectors s3vectors) {

    public record S3Vectors(
            String region,
            String bucket,
            String index,
            Duration connectTimeout,
            Duration apiCallAttemptTimeout,
            Duration apiCallTimeout) { }
}
