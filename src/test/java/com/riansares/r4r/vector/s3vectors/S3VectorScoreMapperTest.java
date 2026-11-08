package com.riansares.r4r.vector.s3vectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class S3VectorScoreMapperTest {
    private final S3VectorScoreMapper mapper = new S3VectorScoreMapper();

    @Test void cosineMappingAndStrictBoundaryMatchPgPredicate() {
        assertThat(mapper.toScore(0)).isEqualTo(1);
        assertThat(mapper.toScore(1)).isZero();
        assertThat(mapper.toScore(2)).isEqualTo(-1);
        double boundary = 1 - .63;
        assertThat(mapper.meetsThreshold(Math.nextDown(boundary), .63)).isTrue();
        assertThat(mapper.meetsThreshold(boundary, .63)).isFalse();
        assertThat(mapper.meetsThreshold(Math.nextUp(boundary), .63)).isFalse();
        assertThat(mapper.meetsThreshold(0, 1)).isFalse();
        assertThat(mapper.meetsThreshold(1, 0)).isFalse();
        assertThat(mapper.meetsThreshold(Math.nextDown(1.0), 0)).isTrue();
    }

    @Test void invalidDistancesAreIntegrityErrorsRatherThanEmptyEvidence() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -.001, 2.001}) {
            assertThatThrownBy(() -> mapper.toScore(value)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> mapper.meetsThreshold(value, .63)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void invalidThresholdsAreRejected() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -.01, 1.01}) {
            assertThatThrownBy(() -> mapper.meetsThreshold(.1, value)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
