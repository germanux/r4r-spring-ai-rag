package com.riansares.r4r.ingestion;

import com.riansares.r4r.R4rSpringAiRagApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.Objects;

public class KnowledgeIngestionCli {

    private KnowledgeIngestionCli() {
        // utility class
    }

    /**
     * Entry point for deterministic production knowledge ingestion.
     *
     * @param args command line arguments (ignored)
     */
    public static void main(String[] args) {
        try (var context = new SpringApplicationBuilder()
                .sources(R4rSpringAiRagApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {

            var orchestration = context.getBean(KnowledgeIngestionOrchestration.class);
            Objects.requireNonNull(orchestration, "KnowledgeIngestionOrchestration bean not found");

            KnowledgeIngestionOrchestration.IngestionResult result;
            try {
                result = orchestration.execute();
            } catch (Exception e) {
                // Capture any unexpected exception and show concise error
                System.err.println("ERROR: Unexpected ingestion failure");
                System.exit(4);
                return;
            }

            if (result.exitCode() != 0) {
                System.exit(result.exitCode());
            }
        } catch (Exception e) {
            System.err.println("ERROR: Application startup failure - " + e.getMessage());
            System.exit(5);
        }
    }
}
