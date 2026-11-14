package com.riansares.r4r.ingestion;

import com.riansares.r4r.R4rSpringAiRagApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * CLI entry point for deterministic production knowledge ingestion.
 * <p>
 * This class creates a non-web Spring context, executes the ingestion orchestration
 * and terminates with an appropriate exit code. The {@link #execute(String[])} method
 * provides a testable adapter that returns the categorized exit code without calling
 * System.exit, allowing proper resource cleanup via try-with-resources.
 */
public class KnowledgeIngestionCli {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_INVALID_CONFIGURATION = 1;
    private static final int EXIT_CODE_INVALID_ROOT = 2;
    private static final int EXIT_CODE_INFRASTRUCTURE_FAILURE = 3;
    private static final int EXIT_CODE_INGESTION_FAILURE = 4;
    private static final int EXIT_CODE_APP_FAILURE = 5;

    /**
     * Exposes the builder configuration for testing.
     *
     * @return a pre-configured SpringApplicationBuilder with WebApplicationType.NONE
     */
    static SpringApplicationBuilder createBuilder() {
        return new SpringApplicationBuilder()
                .sources(R4rSpringAiRagApplication.class)
                .web(WebApplicationType.NONE);
    }

    /**
     * Testable adapter that executes ingestion and returns the exit code.
     * <p>
     * This method creates the context, executes orchestration and properly closes
     * the context before returning. It does not call System.exit, making it suitable
     * for testing.
     *
     * @param args command line arguments (ignored)
     * @return categorized exit code
     */
    static int execute(String[] args) {
        SpringApplicationBuilder builder = createBuilder();
        // CLI-only defaults: a lost NAS must not leave an ingestion JVM waiting indefinitely.
        builder.properties(java.util.Map.of(
                "spring.datasource.hikari.connection-timeout", "5000",
                "spring.datasource.hikari.data-source-properties.connectTimeout", "5",
                "spring.datasource.hikari.data-source-properties.socketTimeout", "30",
                "spring.datasource.hikari.data-source-properties.tcpKeepAlive", "true"));
        try (var context = builder.run(args)) {
            String export = context.getEnvironment().getProperty("r4r.ingestion.export-s3", "");
            if (export != null && !export.isBlank()) {
                long count = context.getBean(com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore.class)
                        .exportS3Vectors(java.nio.file.Path.of(export));
                System.out.println("R4R_S3_EXPORT_RESULT=" + new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(java.util.Map.of("vectors", count, "dimensions", 1024, "file", export)));
                return EXIT_CODE_SUCCESS;
            }
            if (context.getEnvironment().getProperty("r4r.ingestion.index", Boolean.class, false)) {
                try {
                    var result = context.getBean(KnowledgeIngestionService.class).ingestIndexed(
                            context.getEnvironment().getProperty("r4r.ingestion.check-only", Boolean.class, false),
                            context.getEnvironment().getProperty("r4r.ingestion.replace-all", Boolean.class, false));
                    System.out.println("R4R_INGESTION_RESULT=" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result));
                    return EXIT_CODE_SUCCESS;
                } catch (Exception failure) {
                    // Do not print database URLs, credentials or response bodies on failures.
                    System.err.println("ERROR: Complete ingestion failed: " + failure.getClass().getSimpleName());
                    return EXIT_CODE_INGESTION_FAILURE;
                }
            }
            var orchestration = context.getBean(KnowledgeIngestionOrchestration.class);
            if (orchestration == null) {
                System.err.println("ERROR: KnowledgeIngestionOrchestration bean not found");
                return EXIT_CODE_APP_FAILURE;
            }

            var result = orchestration.execute();
            return result.exitCode();
        } catch (Exception e) {
            System.err.println("ERROR: Application startup failure");
            return EXIT_CODE_APP_FAILURE;
        }
    }

    /**
     * Entry point for deterministic production knowledge ingestion.
     *
     * @param args command line arguments (ignored)
     */
    public static void main(String[] args) {
        int exitCode = execute(args);
        System.exit(exitCode);
    }
}
