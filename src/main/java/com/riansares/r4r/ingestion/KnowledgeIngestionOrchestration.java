package com.riansares.r4r.ingestion;

import com.riansares.r4r.config.KnowledgeProperties;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
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
        this.outputSupplier =Objects.requireNonNull(outputSupplier, "outputSupplier must not be null");
        this.errorSupplier = Objects.requireNonNull(errorSupplier, "errorSupplier must not be null");
        this.clockSupplier = Objects.requireNonNull(clockSupplier, "clockSupplier must not be null");
    }

    public IngestionResult execute() {
        KnowledgeProperties properties;
        try {
            properties = propertiesSupplier.get();
        } catch (Exception e) {
            errorSupplier.get().println("ERROR: Failed to load knowledge configuration");
            return new IngestionResult.InvalidConfiguration(e);
        }

        // First check if root exists before canonicalizing
        java.nio.file.Path rootPath = properties.root();
        if (!rootPath.toFile().exists()) {
            errorSupplier.get().println("ERROR: Knowledge root does not exist: " + rootPath);
            return new IngestionResult.InvalidRoot(rootPath);
        }

        // Now get the canonical path (which requires the file to exist)
        java.nio.file.Path canonicalRoot;
        try {
            canonicalRoot = rootPath.toRealPath();
        } catch (java.io.IOException e) {
            errorSupplier.get().println("ERROR: Cannot resolve knowledge root path");
            return new IngestionResult.InvalidConfiguration(e);
        }

        Clock clock = clockSupplier.get();
        Instant start = clock.instant();

        KnowledgeIngestionResult result;
        try {
            result = service.ingest(clock);
        } catch (IllegalStateException e) {
            errorSupplier.get().println("ERROR: Ingestion failed");
            if (isInfrastructureCause(e)) {
                errorSupplier.get().println("ERROR: Infrastructure unavailable (database/model)");
            }
            return new IngestionResult.IngestionFailure(e, isInfrastructureCause(e));
        } catch (Exception e) {
            errorSupplier.get().println("ERROR: Ingestion failed");
            if (isInfrastructureCause(e)) {
                errorSupplier.get().println("ERROR: Infrastructure unavailable (database/model)");
            }
            return new IngestionResult.IngestionFailure(e, isInfrastructureCause(e));
        }

        Instant end = clock.instant();
        long durationMs = java.time.Duration.between(start, end).toMillis();

        KnowledgeIngestionResult orchestrationResult = new KnowledgeIngestionResult(
                result.discoveredSources(),
                result.changedSources(),
                result.unchangedSources(),
                result.deletedSources(),
                result.persistedChunks(),
                durationMs
        );

        String json = toJson(canonicalRoot, orchestrationResult, durationMs);
        outputSupplier.get().println("R4R_INGESTION_RESULT=" + json);

        return new IngestionResult.Success(orchestrationResult);
    }

    private boolean isInfrastructureCause(Throwable cause) {
        if (cause == null) return false;

        // Check the entire cause chain for infrastructure indicators by type using instanceof
        Throwable current = cause;
        while (current != null) {
            // Infrastructure classification by representative typed exceptions:
            // - SQL/Spring Data-access exceptions
            if (current instanceof java.sql.SQLException
                    || current instanceof org.springframework.dao.DataAccessException)
            {
                return true;
            }
            // Network connectivity exceptions
            if (current instanceof java.net.ConnectException
                    || current instanceof java.net.SocketTimeoutException)
            {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private String toJson(java.nio.file.Path canonicalRoot, KnowledgeIngestionResult result, long durationMs) {
        return "{"
                + "\"path\":\"" + escapeJson(canonicalRoot.toString()) + "\","
                + "\"discovered\":" + result.discoveredSources() + ","
                + "\"changed\":" + result.changedSources() + ","
                + "\"unchanged\":" + result.unchangedSources() + ","
                + "\"deleted\":" + result.deletedSources() + ","
                + "\"chunks\":" + result.persistedChunks() + ","
                + "\"durationMs\":" + durationMs + ","
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

        record IngestionFailure(Throwable cause, boolean isInfrastructure) implements IngestionResult {
            @Override
            public int exitCode() {
                if (isInfrastructure) {
                    return 3;
                }
                return 4;
            }
        }
    }

}
