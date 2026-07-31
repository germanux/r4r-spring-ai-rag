package com.riansares.r4r.ingestion;

import com.riansares.r4r.config.KnowledgeProperties;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

public class KnowledgeIngestionOrchestration {

    private final KnowledgeIngestionService service;
    private final Supplier<KnowledgeProperties> propertiesSupplier;
    private final Supplier<PrintStream> outputSupplier;
    private final Supplier<PrintStream> errorSupplier;
    private final Supplier<Clock> clockSupplier;

    public KnowledgeIngestionOrchestration(
            KnowledgeIngestionService service,
            Supplier<KnowledgeProperties> propertiesSupplier) {
        this(service, propertiesSupplier, () -> System.out, () -> System.err, Clock::systemUTC);
    }

    public KnowledgeIngestionOrchestration(
            KnowledgeIngestionService service,
            Supplier<KnowledgeProperties> propertiesSupplier,
            Supplier<PrintStream> outputSupplier,
            Supplier<PrintStream> errorSupplier,
            Supplier<Clock> clockSupplier) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.propertiesSupplier = Objects.requireNonNull(propertiesSupplier, "propertiesSupplier must not be null");
        this.outputSupplier = Objects.requireNonNull(outputSupplier, "outputSupplier must not be null");
        this.errorSupplier = Objects.requireNonNull(errorSupplier, "errorSupplier must not be null");
        this.clockSupplier = Objects.requireNonNull(clockSupplier, "clockSupplier must not be null");
    }

    public IngestionResult execute() {
        KnowledgeProperties properties;
        try {
            properties = propertiesSupplier.get();
        } catch (Exception e) {
            errorSupplier.get().println("ERROR: Failed to load knowledge configuration: " + e.getMessage());
            return new IngestionResult.InvalidConfiguration(e);
        }

        if (!properties.root().toFile().exists()) {
            errorSupplier.get().println("ERROR: Knowledge root does not exist: " + properties.root());
            return new IngestionResult.InvalidRoot(properties.root());
        }

        KnowledgeIngestionResult result;
        try {
            result = service.ingest(clockSupplier.get());
        } catch (IllegalStateException e) {
            errorSupplier.get().println("ERROR: Ingestion failed - " + e.getMessage());
            Throwable cause = e.getCause();
            if (cause != null && !isInfrastructureCause(cause)) {
                errorSupplier.get().println("ERROR: Unknown ingestion failure");
            }
            return new IngestionResult.IngestionFailure(e);
        } catch (Exception e) {
            errorSupplier.get().println("ERROR: Ingestion failed - " + e.getMessage());
            if (isInfrastructureCause(e)) {
                errorSupplier.get().println("ERROR: Infrastructure unavailable (database/model)");
            }
            return new IngestionResult.IngestionFailure(e);
        }

        String json = toJson(properties, result);
        outputSupplier.get().println("R4R_INGESTION_RESULT=" + json);

        return new IngestionResult.Success(result);
    }

    private boolean isInfrastructureCause(Throwable cause) {
        String msg = cause.getMessage() != null ? cause.getMessage() : "";
        String className = cause.getClass().getName();
        return msg.toLowerCase().contains("database")
                || msg.toLowerCase().contains("connection")
                || msg.toLowerCase().contains("postgresql")
                || className.contains("DataAccessException")
                || className.contains("PostgreSQLException");
    }

    private String toJson(KnowledgeProperties properties, KnowledgeIngestionResult result) {
        return "{"
                + "\"path\":\"" + escapeJson(properties.root().toAbsolutePath().normalize().toString()) + "\","
                + "\"discovered\":" + result.discoveredSources() + ","
                + "\"changed\":" + result.changedSources() + ","
                + "\"unchanged\":" + result.unchangedSources() + ","
                + "\"chunks\":" + result.persistedChunks() + ","
                + "\"durationMs\":" + result.durationMs() + ","
                + "\"success\":true"
                + "}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    public sealed interface IngestionResult {
        int exitCode();

        record Success(KnowledgeIngestionResult result) implements IngestionResult {
            @Override
            public int exitCode() {
                return 0;
            }
        }

        record InvalidConfiguration(Throwable cause) implements IngestionResult {
            @Override
            public int exitCode() {
                return 1;
            }
        }

        record InvalidRoot(java.nio.file.Path root) implements IngestionResult {
            @Override
            public int exitCode() {
                return 2;
            }
        }

        record IngestionFailure(Throwable cause) implements IngestionResult {
            @Override
            public int exitCode() {
                Throwable root = findRootCause(cause);
                if (root != null && isDatabaseIssue(root)) {
                    return 3;
                }
                return 4;
            }

            private Throwable findRootCause(Throwable t) {
                while (t.getCause() != null && t.getCause() != t) {
                    t = t.getCause();
                }
                return t;
            }

            private boolean isDatabaseIssue(Throwable t) {
                String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";
                String className = t.getClass().getName();
                return msg.contains("database")
                        || msg.contains("connection")
                        || msg.contains("postgresql")
                        || className.contains("DataAccessException")
                        || className.contains("PostgreSQLException");
            }
        }
    }

}
