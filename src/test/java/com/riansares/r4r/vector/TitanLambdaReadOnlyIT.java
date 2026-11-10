package com.riansares.r4r.vector;

import com.riansares.r4r.vector.config.TitanRetrievalProperties;
import com.riansares.r4r.vector.titan.TitanLambdaKnowledgeStore;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.lambda.LambdaClient;
import static org.assertj.core.api.Assertions.*;

/** Opt-in real Lambda/Titan/S3 retrieval through the production Java adapter; no writes or generation. */
@Tag("aws") @Tag("titan")
@EnabledIfSystemProperty(named = "r4r.test.titan.enabled", matches = "true",
        disabledReason = "NOT RUN: real Titan requires -Ptitan-it, populated Titan index and explicit calibrated similarity")
class TitanLambdaReadOnlyIT {
    static LambdaClient lambda;
    static TitanLambdaKnowledgeStore store;
    @BeforeAll static void open() {
        String threshold = System.getProperty("r4r.test.titan.minimum-similarity");
        if (threshold == null) throw new IllegalArgumentException("Set -Dr4r.test.titan.minimum-similarity after real ES/EN evaluation");
        var config = new TitanRetrievalProperties(true, null, null, Duration.ofSeconds(25), 5, Double.valueOf(threshold));
        config.validate();
        lambda = LambdaClient.builder().region(Region.EU_NORTH_1)
                .credentialsProvider(ProfileCredentialsProvider.builder().profileName("r4r-codex").build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(2)).socketTimeout(config.timeout()))
                .overrideConfiguration(c -> c.apiCallTimeout(config.timeout()).apiCallAttemptTimeout(config.timeout())
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())).build();
        store = new TitanLambdaKnowledgeStore(lambda, config);
    }
    @AfterAll static void close() { if (lambda != null) lambda.close(); }
    @ParameterizedTest @MethodSource("com.riansares.r4r.vector.TitanDegradedHttpIT#canonicalCases")
    void realBilingualRetrievalPreservesEvidenceAndAbstainsOutsideCorpus(String language, String question, boolean answerable) {
        var chunks = store.search(question, 5, .63); // Nomic threshold ignored by the Titan adapter.
        assertThat(chunks.isEmpty()).as(language + " / " + question).isEqualTo(!answerable);
        chunks.forEach(chunk -> {
            assertThat(chunk.source()).isNotBlank(); assertThat(chunk.content()).isNotBlank(); assertThat(chunk.index()).isNotNegative();
        });
    }
}
