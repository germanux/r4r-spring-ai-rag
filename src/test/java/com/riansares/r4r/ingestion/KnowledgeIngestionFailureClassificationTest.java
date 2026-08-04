package com.riansares.r4r.ingestion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for failure classification in KnowledgeIngestionOrchestration.
 * <p>
 * Verifies that infrastructure failures (SQLException, Spring DataAccessException,
 * ConnectException, SocketTimeoutException) are classified with exit code 3,
 * configuration failures use exit code 1, and generic failures use exit code 4.
 */
class KnowledgeIngestionFailureClassificationTest {

    private Path tempRoot;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() throws IOException {
        tempRoot = Files.createTempDirectory("r4r-failure-test-");
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

    // ==========================
    // Infrastructure failures (exit code 3)
    // ==========================

    @Test
    void executeWithSqlExceptionCauseReturnsExitCode3() {
        var mockService = mock(KnowledgeIngestionService.class);
        SQLException sqlException = new SQLException("Connection refused", "08001");
        IllegalStateException wrappedException = new IllegalStateException(
                "Failed to ingest knowledge documents",
                sqlException);
        when(mockService.ingest(any())).thenThrow(wrappedException);

        var orchestration = createOrchestration(mockService, tempRoot);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).as("SQLException cause should return infrastructure exit code 3").isEqualTo(3);

        String err = errContent.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
        assertThat(err).contains("ERROR: Infrastructure unavailable");
    }

    @Test
    void executeWithSpringDataAccessExceptionCauseReturnsExitCode3() {
        var mockService = mock(KnowledgeIngestionService.class);
        org.springframework.dao.DataAccessException daoException =
                new org.springframework.dao.DataAccessResourceFailureException("Database resource unavailable");
        IllegalStateException wrappedException = new IllegalStateException(
                "Failed to ingest knowledge documents",
                daoException);
        when(mockService.ingest(any())).thenThrow(wrappedException);

        var orchestration = createOrchestration(mockService, tempRoot);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).as("Spring DataAccessException cause should return infrastructure exit code 3").isEqualTo(3);

        String err = errContent.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
        assertThat(err).contains("ERROR: Infrastructure unavailable");
    }

    @Test
    void executeWithConnectExceptionCauseReturnsExitCode3() {
        var mockService = mock(KnowledgeIngestionService.class);
        ConnectException connectException = new ConnectException("Connection refused to Ollama service");
        IllegalStateException wrappedException = new IllegalStateException(
                "Failed to ingest knowledge documents",
                connectException);
        when(mockService.ingest(any())).thenThrow(wrappedException);

        var orchestration = createOrchestration(mockService, tempRoot);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).as("ConnectException cause should return infrastructure exit code 3").isEqualTo(3);

        String err = errContent.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
        assertThat(err).contains("ERROR: Infrastructure unavailable");
    }

    @Test
    void executeWithSocketTimeoutExceptionCauseReturnsExitCode3() {
        var mockService = mock(KnowledgeIngestionService.class);
        SocketTimeoutException timeoutException = new SocketTimeoutException("Connection timed out to model service");
        IllegalStateException wrappedException = new IllegalStateException(
                "Failed to ingest knowledge documents",
                timeoutException);
        when(mockService.ingest(any())).thenThrow(wrappedException);

        var orchestration = createOrchestration(mockService, tempRoot);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).as("SocketTimeoutException cause should return infrastructure exit code 3").isEqualTo(3);

        String err = errContent.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
        assertThat(err).contains("ERROR: Infrastructure unavailable");
    }

    @Test
    void executeWithMultipleWrappedInfrastructureExceptionsReturnsExitCode3() {
        var mockService = mock(KnowledgeIngestionService.class);
        // SQLException -> ConnectException chain (double wrapped in IllegalStateException)
        SQLException sqlException = new SQLException("SQL error");
        ConnectException connectException = new ConnectException("Connection failed");
        connectException.initCause(sqlException);
        IllegalStateException topLevelException = new IllegalStateException(
                "Failed to ingest knowledge documents",
                connectException);
        when(mockService.ingest(any())).thenThrow(topLevelException);

        var orchestration = createOrchestration(mockService, tempRoot);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).as("Deeply wrapped infrastructure exceptions should return exit code 3").isEqualTo(3);

        String err = errContent.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Infrastructure unavailable");
    }

    // ==========================
    // Generic failures (exit code 4)
    // ==========================

    @Test
    void executeWithGenericRuntimeExceptionReturnsExitCode4() {
        var mockService = mock(KnowledgeIngestionService.class);
        RuntimeException genericException = new RuntimeException("Unknown failure occurred");
        when(mockService.ingest(any())).thenThrow(genericException);

        var orchestration = createOrchestration(mockService, tempRoot);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).as("Generic RuntimeException should return ingestion failure exit code 4").isEqualTo(4);

        String err = errContent.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
        // Should NOT contain infrastructure message
        assertThat(err).doesNotContain("ERROR: Infrastructure unavailable");
    }

    @Test
    void executeWithRuntimeExceptionReturnsExitCode4() {
        var mockService = mock(KnowledgeIngestionService.class);
        RuntimeException runtimeException = new RuntimeException("Runtime error");
        when(mockService.ingest(any())).thenThrow(runtimeException);

        var orchestration = createOrchestration(mockService, tempRoot);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).as("RuntimeException should return ingestion failure exit code 4").isEqualTo(4);

        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
        assertThat(err).doesNotContain("ERROR: Infrastructure unavailable");
    }

    @Test
    void executeWithIOExceptionWrappedInIllegalStateExceptionReturnsExitCode4() {
        var mockService = mock(KnowledgeIngestionService.class);
        IOException ioException = new IOException("File read error");
        IllegalStateException wrappedException = new IllegalStateException(
                "Failed to ingest knowledge documents",
                ioException);
        when(mockService.ingest(any())).thenThrow(wrappedException);

        var orchestration = createOrchestration(mockService, tempRoot);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).as("IOException wrapped in IllegalStateException without infrastructure type should return exit code 4").isEqualTo(4);

        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
        assertThat(err).doesNotContain("ERROR: Infrastructure unavailable");
    }

    @Test
    void executeWithMessageThatResemblesDbFailureButNoTypedCauseReturnsExitCode4() {
        var mockService = mock(KnowledgeIngestionService.class);
        // Exception with message resembling database failure but NO actual SQLException cause
        RuntimeException misleadingException = new IllegalStateException(
                "Database connection failed: postgresql://user:pass@localhost:5432/db");
        when(mockService.ingest(any())).thenThrow(misleadingException);

        var orchestration = createOrchestration(mockService, tempRoot);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).as("Message resembling DB failure without typed cause should return exit code 4").isEqualTo(4);

        String err = errContent.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
        assertThat(err).doesNotContain("ERROR: Infrastructure unavailable");
        // Security: credentials must be redacted even from generic failures
        assertThat(err).doesNotContain("pass")
                .doesNotContain("postgresql://user:");
    }

    @Test
    void executeWithConnectExceptionMessageButNoTypedCauseReturnsExitCode4() {
        var mockService = mock(KnowledgeIngestionService.class);
        // Exception with message resembling connection failure but NO actual ConnectException cause
        RuntimeException misleadingException = new IllegalStateException(
                "Connection refused to database service");
        when(mockService.ingest(any())).thenThrow(misleadingException);

        var orchestration = createOrchestration(mockService, tempRoot);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        assertThat(result.exitCode()).as("Message resembling connection failure without typed cause should return exit code 4").isEqualTo(4);

        String err = errContent.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
        assertThat(err).doesNotContain("ERROR: Infrastructure unavailable");
    }

    // ==========================
    // Configuration failures (exit code 1)
    // ==========================

    @Test
    void executeWhenPropertiesSupplierThrowsExceptionReturnsInvalidConfigurationExitCode1() {
        var mockService = mock(KnowledgeIngestionService.class);
        IllegalArgumentException configException = new IllegalArgumentException("Missing required property: r4r.knowledge.root");

        Supplier<com.riansares.r4r.config.KnowledgeProperties> failingSupplier = () -> {
            throw configException;
        };

        var orchestration = new KnowledgeIngestionOrchestration(
                mockService,
                failingSupplier,
                () -> new PrintStream(outContent, true),
                () -> new PrintStream(errContent, true),
                Clock::systemUTC);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.InvalidConfiguration.class);
        assertThat(result.exitCode()).as("Configuration supplier exception should return invalid configuration exit code 1").isEqualTo(1);

        String err = errContent.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Failed to load knowledge configuration");
    }

    @Test
    void executeWhenPropertiesSupplierThrowsNullPointerExceptionReturnsInvalidConfigurationExitCode1() {
        var mockService = mock(KnowledgeIngestionService.class);
        NullPointerException nullException = new NullPointerException("Root path is null");

        Supplier<com.riansares.r4r.config.KnowledgeProperties> failingSupplier = () -> {
            throw nullException;
        };

        var orchestration = new KnowledgeIngestionOrchestration(
                mockService,
                failingSupplier,
                () -> new PrintStream(outContent, true),
                () -> new PrintStream(errContent, true),
                Clock::systemUTC);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.InvalidConfiguration.class);
        assertThat(result.exitCode()).as("NPE in properties supplier should return invalid configuration exit code 1").isEqualTo(1);
    }

    @Test
    void executeWhenPropertiesSupplierThrowsIllegalStateExceptionReturnsInvalidConfigurationExitCode1() {
        var mockService = mock(KnowledgeIngestionService.class);
        IllegalStateException stateException = new IllegalStateException("Invalid configuration state");

        Supplier<com.riansares.r4r.config.KnowledgeProperties> failingSupplier = () -> {
            throw stateException;
        };

        var orchestration = new KnowledgeIngestionOrchestration(
                mockService,
                failingSupplier,
                () -> new PrintStream(outContent, true),
                () -> new PrintStream(errContent, true),
                Clock::systemUTC);

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.InvalidConfiguration.class);
        assertThat(result.exitCode()).as("IllegalStateException in properties supplier should return invalid configuration exit code 1").isEqualTo(1);
    }

    // ==========================
    // Utility methods
    // ==========================

    private KnowledgeIngestionOrchestration createOrchestration(KnowledgeIngestionService service, Path root) {
        var properties = new com.riansares.r4r.config.KnowledgeProperties(root, 1_048_576, 2_000);
        return new KnowledgeIngestionOrchestration(
                service,
                () -> properties,
                () -> new PrintStream(outContent, true),
                () -> new PrintStream(errContent, true),
                Clock::systemUTC);
    }
}
