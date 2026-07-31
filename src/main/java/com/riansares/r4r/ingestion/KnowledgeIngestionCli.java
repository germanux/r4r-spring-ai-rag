package com.riansares.r4r.ingestion;

import com.riansares.r4r.R4rSpringAiRagApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.Objects;

public class KnowledgeIngestionCli {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_APP_FAILURE = 5;
    private static final int EXIT_CODE_INGESTION_FAILURE = 4;

    /**
     * Exposes the builder configuration seam for testing.
     *
     * @return a pre-configured SpringApplicationBuilder with WebApplicationType.NONE
     */
    static SpringApplicationBuilder createBuilder() {
        return new SpringApplicationBuilder()
                .sources(R4rSpringAiRagApplication.class)
                .web(WebApplicationType.NONE);
    }

    /**
     * Entry point for deterministic production knowledge ingestion.
     *
     * @param args command line arguments (ignored)
     */
    public static void main(String[] args) {
        try (var context = createBuilder().run(args)) {

            var orchestration = context.getBean(KnowledgeIngestionOrchestration.class);
            Objects.requireNonNull(orchestration, "KnowledgeIngestionOrchestration bean not found");

            KnowledgeIngestionOrchestration.IngestionResult result;
            try {
                result = orchestration.execute();
            } catch (Exception e) {
                // Capture any unexpected exception and show concise error
                System.err.println("ERROR: Unexpected ingestion failure");
                System.exit(EXIT_CODE_APP_FAILURE);
                return;
            }

            if (result.exitCode() != EXIT_CODE_SUCCESS) {
                System.exit(result.exitCode());
            }
        } catch (Exception e) {
            System.err.println("ERROR: Application startup failure - " + e.getMessage());
            System.exit(EXIT_CODE_APP_FAILURE);
        }
    }
}
