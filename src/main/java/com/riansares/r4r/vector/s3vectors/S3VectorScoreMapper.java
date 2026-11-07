package com.riansares.r4r.vector.s3vectors;

/**
 * Phase 3 skeleton: cosine distance conversion and strict threshold comparison.
 * Numerical equivalence with Spring AI/pgvector must be verified before use;
 * neither conversion nor validation is implemented here.
 */
public final class S3VectorScoreMapper {

    public double toScore(double cosineDistance) {
        throw new UnsupportedOperationException("S3 Vectors score conversion is not implemented");
    }

    public boolean meetsThreshold(double cosineDistance, double minScore) {
        throw new UnsupportedOperationException("S3 Vectors threshold comparison is not implemented");
    }
}
