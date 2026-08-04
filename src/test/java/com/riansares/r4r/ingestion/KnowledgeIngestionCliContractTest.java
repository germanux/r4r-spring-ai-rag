package com.riansares.r4r.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.config.KnowledgeProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract test for KnowledgeIngestionOrchestration.
 * <p>
 * Uses mocked dependencies, controlled output/error streams and deterministic clock.
 * No live Ollama, PostgreSQL or Spring context is started.
 */
class KnowledgeIngestionCliContractTest {

    private Path tempRoot;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() throws IOException {
        tempRoot = Files.createTempDirectory("r4r-knowledge-cli-contract-test-");
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempRoot != null) {
            try {
                deleteRecursively(tempRoot);
            } catch (IOException e) {
                // ignore cleanup errors
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(p -> {
                    try {
                        deleteRecursively(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
            }
        }
        Files.deleteIfExists(path);
    }

    @Test
    void executeSuccessOutputsExactlyOnePrefixedJsonWithAllRequiredFields() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        // Use non-zero deletion count to verify propagation
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(3, 1, 2, 7, 5, 42));
        var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
        var orchestration = createOrchestration(mockService, properties);

        var result = orchestration.execute();

        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);

        String output = outContent.toString(StandardCharsets.UTF_8);
        String[] lines = output.split("\\r?\\n", -1);

        // Exactly one final line with R4R_INGESTION_RESULT=
        long resultLineCount = 0;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty() && lines[i].startsWith("R4R_INGESTION_RESULT=")) {
                resultLineCount++;
            }
        }
        assertThat(resultLineCount).as("Exactly one output line must have R4R_INGESTION_RESULT= prefix").isEqualTo(1);

        // Parse the JSON from the last occurrence (handles trailing newlines)
        String json = output.substring(output.lastIndexOf("R4R_INGESTION_RESULT=") + "R4R_INGESTION_RESULT=".length()).trim();

        var mapper = new ObjectMapper();
        var node = mapper.readTree(json);
        assertThat(node.isObject()).as("JSON should be an object").isTrue();

        // Assert all required fields
        assertThat(node.get("path")).isNotNull();
        assertThat(node.get("discovered").asInt()).isEqualTo(3);
        assertThat(node.get("changed").asInt()).isEqualTo(1);
        assertThat(node.get("unchanged").asInt()).isEqualTo(2);
        assertThat(node.get("deleted").asInt()).isEqualTo(7);
        assertThat(node.get("chunks").asInt()).isEqualTo(5);
        long duration = node.get("durationMs").asLong();
        assertThat(duration).isNotNegative();
        assertThat(node.get("success").asBoolean()).isTrue();

        // Verify the success result contains the exact deletion count
        var successResult = (KnowledgeIngestionOrchestration.IngestionResult.Success) result;
        assertThat(successResult.result().deletedSources()).isEqualTo(7);

        verify(mockService, times(1)).ingest(any());
    }

    @Test
    void executeDelegatesIngestionExactlyOnce() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 0, 42));
        var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
        var orchestration = createOrchestration(mockService, properties);

        // First execution
        orchestration.execute();
        verify(mockService, times(1)).ingest(any());

        // Second execution should delegate again
        orchestration.execute();
        verify(mockService, times(2)).ingest(any());
    }

    @Test
    void canonicalPathIsUsedInResult() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 0, 42));

        // Create a directory and a symlink to it
        Path realDir = Files.createDirectories(tempRoot.resolve("real"));
        Path symlink = tempRoot.resolve("link");

        // Try to create symlink - skip if not supported
        assumeTrue(createSymlink(symlink, realDir), "Symbolic links are not supported on this platform");

        var properties = new KnowledgeProperties(symlink, 1_048_576, 2_000);
        var orchestration = createOrchestration(mockService, properties);

        var result = orchestration.execute();

        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);

        String output = outContent.toString(StandardCharsets.UTF_8);
        String json = output.substring(output.lastIndexOf("R4R_INGESTION_RESULT=") + "R4R_INGESTION_RESULT=".length()).trim();
        var mapper = new ObjectMapper();
        var node = mapper.readTree(json);

        // The path in JSON should be the resolved canonical path (symlink target)
        String emittedPath = node.get("path").asText();

        // Assert the emitted path equals realDir.toRealPath().toString() directly
        assertThat(emittedPath).isEqualTo(realDir.toRealPath().toString());

        // Assert the emitted path differs from the symlink path itself
        assertThat(emittedPath).isNotEqualTo(symlink.toString());
    }

    @Test
    void invalidRoot_returnsExitCode2_withSanitizedError() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        Path nonExistentRoot = tempRoot.resolve("non-existent");
        var properties = new KnowledgeProperties(nonExistentRoot, 1_048_576, 2_000);
        var orchestration = createOrchestration(mockService, properties);

        var result = orchestration.execute();

        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.InvalidRoot.class);
        assertThat(result.exitCode()).isEqualTo(2);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Knowledge root does not exist");
    }

    @Test
    void infrastructureFailure_returnsExitCode3_withSanitizedError() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        IllegalStateException infraException = new IllegalStateException(
                "Failed to ingest",
                new java.sql.SQLException("Connection refused", "08001"));
        when(mockService.ingest(any())).thenThrow(infraException);

        var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
        var orchestration = createOrchestration(mockService, properties);

        var result = orchestration.execute();

        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).isEqualTo(3);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Infrastructure unavailable");
    }

    @Test
    void genericFailure_returnsExitCode4_withSanitizedError() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        RuntimeException genericException = new RuntimeException("Unknown failure");
        when(mockService.ingest(any())).thenThrow(genericException);

        var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
        var orchestration = createOrchestration(mockService, properties);

        var result = orchestration.execute();

        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).isEqualTo(4);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
    }

    @Test
    void successContainsExactDurationInJson() throws Exception {
        final long EXPECTED_DURATION_MS = 1234L;

        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 0, EXPECTED_DURATION_MS));

        // Create a deterministic clock supplier with controlled instants
        var testingClockSupplier = new TestingClockSupplier(Instant.parse("2026-08-01T00:00:00Z"), EXPECTED_DURATION_MS);

        var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
        var orchestration = createOrchestration(mockService, properties, testingClockSupplier);

        var result = orchestration.execute();

        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);

        String output = outContent.toString(StandardCharsets.UTF_8);
        String json = output.substring(output.lastIndexOf("R4R_INGESTION_RESULT=") + "R4R_INGESTION_RESULT=".length()).trim();
        var mapper = new ObjectMapper();
        var node = mapper.readTree(json);

        assertThat(node.get("durationMs").asLong()).isEqualTo(EXPECTED_DURATION_MS);
    }

    private KnowledgeIngestionOrchestration createOrchestration(
            KnowledgeIngestionService mockService,
            KnowledgeProperties properties) {
        return createOrchestration(mockService, properties, Clock::systemUTC);
    }

    private KnowledgeIngestionOrchestration createOrchestration(
            KnowledgeIngestionService mockService,
            KnowledgeProperties properties,
            Supplier<Clock> clockSupplier) {
        return new KnowledgeIngestionOrchestration(
                mockService,
                () -> properties,
                () -> new PrintStream(outContent, true),
                () -> new PrintStream(errContent, true),
                clockSupplier);
    }

    /**
     * Creates a symbolic link from symlinkPath to targetPath.
     * Returns true if successful, false if the platform doesn't support symlinks.
     */
    private boolean createSymlink(Path symlinkPath, Path targetPath) {
        try {
            Files.createSymbolicLink(symlinkPath, targetPath);
            return true;
        } catch (UnsupportedOperationException e) {
            // Platform doesn't support symbolic links
            return false;
        } catch (IOException e) {
            // Other I/O error - assume not supported
            return false;
        }
    }

    /**
     * Testing clock supplier that returns controlled instants for deterministic duration.
     */
    private static class TestingClockSupplier implements Supplier<Clock> {
        private final Instant start;
        private final long durationMs;
        private boolean firstCall = true;

        TestingClockSupplier(Instant start, long durationMs) {
            this.start = start;
            this.durationMs = durationMs;
        }

        @Override
        public Clock get() {
            return new TestClock(start, durationMs);
        }
    }

    /**
     * Inner clock implementation for deterministic duration.
     */
    private static class TestClock extends Clock {
        private final Instant start;
        private final long durationMs;
        private boolean firstCall = true;

        TestClock(Instant start, long durationMs) {
            this.start = start;
            this.durationMs = durationMs;
        }

        @Override
        public Instant instant() {
            if (firstCall) {
                firstCall = false;
                return start;
            }
            return start.plusMillis(durationMs);
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }
    }
}
