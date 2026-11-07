package com.riansares.r4r.vector.s3vectors;

/** Cosine score mapping compatible with Spring AI PG's strict distance predicate. */
public final class S3VectorScoreMapper {
    public double toScore(double cosineDistance) {
        validateDistance(cosineDistance);
        return 1.0 - cosineDistance;
    }

    public boolean meetsThreshold(double cosineDistance, double minScore) {
        validateDistance(cosineDistance);
        if (!Double.isFinite(minScore) || minScore < 0 || minScore > 1) {
            throw new IllegalArgumentException("minScore must be finite and between 0.0 and 1.0");
        }
        return cosineDistance < 1.0 - minScore;
    }

    private static void validateDistance(double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > 2) {
            throw new IllegalStateException("S3 Vectors returned an invalid cosine distance");
        }
    }
}
