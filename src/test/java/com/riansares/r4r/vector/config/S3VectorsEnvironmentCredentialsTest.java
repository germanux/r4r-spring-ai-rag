package com.riansares.r4r.vector.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import static org.assertj.core.api.Assertions.*;

/** SDK default-chain verification with a clean child environment, never profiles or AWS HTTP calls. */
class S3VectorsEnvironmentCredentialsTest {
    @TempDir Path fixture;

    @Test void sdkResolvesBasicEnvironmentCredentialsAndExplicitRegion() throws Exception { probe(false); }
    @Test void sdkResolvesTemporaryEnvironmentCredentialsAndNonDefaultRegion() throws Exception { probe(true); }

    private void probe(boolean temporary) throws Exception {
        var process=new ProcessBuilder(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-cp",System.getProperty("java.class.path"),EnvironmentProbe.class.getName(),Boolean.toString(temporary),temporary ? "eu-west-1" : "eu-north-1"))
                .redirectErrorStream(true);
        var env=process.environment(); env.clear();
        env.put("AWS_ACCESS_KEY_ID","SYNTHETIC_TEST_ACCESS");
        env.put("AWS_SECRET_ACCESS_KEY","synthetic-test-secret");
        env.put("AWS_SESSION_TOKEN",temporary ? "synthetic-test-session" : ""); // Mirrors Compose's optional empty token.
        env.put("AWS_REGION","us-east-1"); // Must not override the explicit RAG S3 region.
        env.put("AWS_EC2_METADATA_DISABLED","true");
        env.put("AWS_CONFIG_FILE",fixture.resolve("absent-config").toString());
        env.put("AWS_SHARED_CREDENTIALS_FILE",fixture.resolve("absent-credentials").toString());
        var running=process.start();
        try {
            assertThat(running.waitFor(20,TimeUnit.SECONDS)).as("Isolated SDK probe finishes without metadata/network access").isTrue();
            String output=new String(running.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertThat(running.exitValue()).as("SDK environment resolution succeeds").isZero();
            assertThat(output).contains("SDK_ENVIRONMENT_OK")
                    .doesNotContain("SYNTHETIC_TEST_ACCESS","synthetic-test-secret","synthetic-test-session");
        } finally { if (running.isAlive()) running.destroyForcibly(); }
    }

    /** A standalone JVM avoids changing the test runner's real environment or global SDK properties. */
    public static class EnvironmentProbe {
        public static void main(String[] args) {
            boolean temporary=Boolean.parseBoolean(args[0]);
            var settings=new RagVectorProperties.S3Vectors(args[1],"synthetic-vector-bucket","synthetic-vector-index",
                    Duration.ofSeconds(1),Duration.ofSeconds(2),Duration.ofSeconds(3));
            try (var client=new S3VectorsConfiguration().s3VectorsClient(new RagVectorProperties("pgvector",true,settings))) {
                var configuration=client.serviceClientConfiguration();
                if (!configuration.region().id().equals(args[1])) throw new IllegalStateException();
                var credentials=((AwsCredentialsProvider)configuration.credentialsProvider()).resolveCredentials();
                if (!credentials.accessKeyId().equals("SYNTHETIC_TEST_ACCESS")
                        || !credentials.secretAccessKey().equals("synthetic-test-secret")) throw new IllegalStateException();
                if (temporary != (credentials instanceof AwsSessionCredentials)) throw new IllegalStateException();
                if (temporary && !((AwsSessionCredentials)credentials).sessionToken().equals("synthetic-test-session"))
                    throw new IllegalStateException();
                System.out.println("SDK_ENVIRONMENT_OK");
            } catch (Throwable failure) {
                // Never print credentials, provider diagnostics or profile paths, even on failure.
                System.out.println("SDK_ENVIRONMENT_FAILED"); System.exit(1);
            }
        }
    }

    @Test void disabledS3StartsWithoutCredentialsAndDoesNotConstructClient() {
        new ApplicationContextRunner().withUserConfiguration(S3VectorsConfiguration.class)
                .withPropertyValues("r4r.rag.vector.s3vectors.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(S3VectorsClient.class));
    }
}
