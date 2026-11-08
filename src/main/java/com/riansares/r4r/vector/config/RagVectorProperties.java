package com.riansares.r4r.vector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import software.amazon.awssdk.regions.Region;

import java.time.Duration;

/** S3 is an opt-in read-only secondary; these properties never select a primary bean. */
@ConfigurationProperties("r4r.rag.vector")
public record RagVectorProperties(
        @DefaultValue("pgvector") String primary,
        @DefaultValue("false") boolean failoverEnabled,
        @DefaultValue S3Vectors s3vectors) {

    public record S3Vectors(
            @DefaultValue("eu-north-1") String region,
            String bucket,
            String index,
            @DefaultValue("1s") Duration connectTimeout,
            @DefaultValue("3s") Duration apiCallAttemptTimeout,
            @DefaultValue("4s") Duration apiCallTimeout) {

        /** Local validation only: no AWS request or credential resolution. */
        public void validate() {
            if (region == null || Region.regions().stream().noneMatch(r -> r.id().equals(region))) {
                throw new IllegalArgumentException("S3 Vectors region must be a known explicit AWS region");
            }
            requireResourceName(bucket, "bucket");
            requireResourceName(index, "index");
            requireTimeout(connectTimeout, "connect-timeout");
            requireTimeout(apiCallAttemptTimeout, "api-call-attempt-timeout");
            requireTimeout(apiCallTimeout, "api-call-timeout");
            if (connectTimeout.compareTo(apiCallAttemptTimeout) > 0
                    || apiCallAttemptTimeout.compareTo(apiCallTimeout) > 0) {
                throw new IllegalArgumentException("S3 Vectors timeouts must satisfy connect <= attempt <= call");
            }
        }

        private static void requireResourceName(String value, String field) {
            if (value == null || !value.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) {
                throw new IllegalArgumentException("S3 Vectors " + field + " must be a valid 3-63 character name");
            }
        }

        private static void requireTimeout(Duration value, String field) {
            if (value == null || value.compareTo(Duration.ofMillis(1)) < 0
                    || value.compareTo(Duration.ofMinutes(2)) > 0) {
                throw new IllegalArgumentException("S3 Vectors " + field + " must be between 1ms and 2m");
            }
        }
    }
}
