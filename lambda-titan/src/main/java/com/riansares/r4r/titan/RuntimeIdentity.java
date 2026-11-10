package com.riansares.r4r.titan;

import java.time.Duration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.sts.StsClient;

/** Read-only diagnostic to run INSIDE the backend container, inheriting its runtime credential chain. */
public final class RuntimeIdentity {
    public static void main(String[] args) {
        try (var sts = StsClient.builder().region(Region.EU_NORTH_1)
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(1)).socketTimeout(Duration.ofSeconds(4)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5)).apiCallAttemptTimeout(Duration.ofSeconds(5))
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())).build()) {
            var identity = sts.getCallerIdentity();
            System.out.println(TitanEmbeddingClient.JSON.writeValueAsString(java.util.Map.of("account", identity.account(), "arn", identity.arn())));
        } catch (Exception failure) {
            System.err.println("Runtime identity unavailable: " + failure.getClass().getSimpleName()); System.exit(1);
        }
    }
}
