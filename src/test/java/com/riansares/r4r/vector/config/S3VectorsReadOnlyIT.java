package com.riansares.r4r.vector.config;

import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.services.s3vectors.model.DistanceMetric;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.VectorData;
import static org.assertj.core.api.Assertions.assertThat;

/** Read-only AWS smoke test against an explicitly selected, pre-existing test index. */
@Tag("aws")
@EnabledIfSystemProperty(named = "r4r.test.aws.enabled", matches = "true",
        disabledReason = "NOT RUN: real AWS requires -Paws-it and a dedicated test index/credentials")
class S3VectorsReadOnlyIT {
    @Test void queriesDedicatedCosineIndexWithoutDatabaseOrLlm() {
        String bucket = required("R4R_TEST_S3_BUCKET");
        String index = required("R4R_TEST_S3_INDEX");
        var config = new RagVectorProperties.S3Vectors(
                System.getenv().getOrDefault("R4R_TEST_S3_REGION", "eu-north-1"), bucket, index,
                Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(4));
        var vector = new java.util.ArrayList<>(Collections.nCopies(1024, 0f));
        vector.set(0, 1f);
        try (var client = new S3VectorsConfiguration().s3VectorsClient(new RagVectorProperties("s3vectors", false, config))) {
            var response = client.queryVectors(QueryVectorsRequest.builder()
                    .vectorBucketName(bucket).indexName(index).topK(1)
                    .queryVector(VectorData.fromFloat32(vector)).returnDistance(true).returnMetadata(false).build());
            assertThat(response.sdkHttpResponse().isSuccessful()).isTrue();
            assertThat(response.distanceMetric()).isEqualTo(DistanceMetric.COSINE);
            assertThat(response.vectors()).hasSizeLessThanOrEqualTo(1);
        }
    }

    private static String required(String name) {
        String value = System.getenv().getOrDefault(name, "");
        assertThat(value.isBlank()).as(name + " must explicitly select a dedicated AWS test resource").isFalse();
        return value;
    }
}
