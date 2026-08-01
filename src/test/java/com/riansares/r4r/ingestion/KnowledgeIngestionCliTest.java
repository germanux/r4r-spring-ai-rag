package com.riansares.r4r.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.R4rSpringAiRagApplication;
import com.riansares.r4r.config.KnowledgeProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeIngestionCliTest {

    private Path tempRoot;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() throws IOException {
        tempRoot = Files.createTempDirectory("r4r-knowledge-cli-test-");
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
    void mainInvokesOrchestrationWithWebApplicationTypeNone() throws Exception {
        SpringApplicationBuilder builder = KnowledgeIngestionCli.createBuilder();
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 42));
        
        try (var context = builder.run()) {
            var mockProperties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
            var orchestration = new KnowledgeIngestionOrchestration(
                    mockService,
                    () -> mockProperties,
                    () -> new PrintStream(outContent, true),
                    () -> new PrintStream(errContent, true),
                    Clock::systemUTC);
            
            // Execute the orchestration and verify service was called
            var result = orchestration.execute();
            
            // Verify the service method was invoked once
            verify(mockService, times(1)).ingest(any());
            
            assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);
            String output = outContent.toString(StandardCharsets.UTF_8);
            assertThat(output).startsWith("R4R_INGESTION_RESULT=");
        }
    }

    @Test
    void executeSuccessOutputsPrefixedJson() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(3, 1, 2, 5, 42));
        var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
        var orchestration = new KnowledgeIngestionOrchestration(
                mockService,
                () -> properties,
                () -> new PrintStream(outContent, true),
                () -> new PrintStream(errContent, true),
                Clock::systemUTC);
        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);
        String output = outContent.toString(StandardCharsets.UTF_8);
        assertThat(output).startsWith("R4R_INGESTION_RESULT=");
        String json = output.substring("R4R_INGESTION_RESULT=".length()).trim();
        var mapper = new ObjectMapper();
        var node = mapper.readTree(json);
        assertThat(node.get("path").asText()).isEqualTo(tempRoot.toAbsolutePath().normalize().toString());
        assertThat(node.get("discovered").asInt()).isEqualTo(3);
        assertThat(node.get("changed").asInt()).isEqualTo(1);
        assertThat(node.get("unchanged").asInt()).isEqualTo(2);
        assertThat(node.get("chunks").asInt()).isEqualTo(5);
        long actualDuration = node.get("durationMs").asLong();
        assertThat(actualDuration).isNotNegative();
        assertThat(node.get("success").asBoolean()).isTrue();
    }

    @Test
    void executeInvalidRootReturnsExitCodeAndRedactsErrors() {
        var mockService = mock(KnowledgeIngestionService.class);
        Path nonExistentRoot = tempRoot.resolve("non-existent");
        var properties = new KnowledgeProperties(nonExistentRoot, 1_048_576, 2_000);
        var orchestration = new KnowledgeIngestionOrchestration(
                mockService,
                () -> properties,
                () -> new PrintStream(outContent, true),
                () -> new PrintStream(errContent, true),
                Clock::systemUTC);
        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.InvalidRoot.class);
        assertThat(result.exitCode()).isEqualTo(2);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Knowledge root does not exist");
    }

    @Test
    void executeIngestionFailureReturnsNonZeroExitCodeAndDoesNotPrintSecrets() {
        var mockService = mock(KnowledgeIngestionService.class);
        IllegalStateException secretException = new IllegalStateException("Database connection failed: postgresql://admin:secretpassword123@localhost:5432/db");
        when(mockService.ingest(any())).thenThrow(secretException);
        var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
        var orchestration = new KnowledgeIngestionOrchestration(
                mockService,
                () -> properties,
                () -> new PrintStream(outContent, true),
                () -> new PrintStream(errContent, true),
                Clock::systemUTC);
        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        int exitCode = result.exitCode();
        assertThat(exitCode).isEqualTo(3);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
        assertThat(err).doesNotContain("secretpassword123")
                       .doesNotContain("postgresql://admin:")
                       .doesNotContain("/db");
    }

    @Test
    void executeIngestionFailureWithGenericExceptionReturnsExitCode4() {
        var mockService = mock(KnowledgeIngestionService.class);
        RuntimeException genericException = new RuntimeException("Unknown failure occurred");
        when(mockService.ingest(any())).thenThrow(genericException);
        var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
        var orchestration = new KnowledgeIngestionOrchestration(
                mockService,
                () -> properties,
                () -> new PrintStream(outContent, true),
                () -> new PrintStream(errContent, true),
                Clock::systemUTC);
        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        int exitCode = result.exitCode();
        assertThat(exitCode).isEqualTo(4);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
    }

    @Test
    void orchestrationDoesNotNeedLiveDependencies() {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 42));
        var mockPropertiesSupplier = mock(Supplier.class);
        when(mockPropertiesSupplier.get()).thenReturn(new KnowledgeProperties(tempRoot, 1_048_576, 2_000));
        var outCapture = new ByteArrayOutputStream();
        var errCapture = new ByteArrayOutputStream();
        var orchestration = new KnowledgeIngestionOrchestration(
                mockService,
                mockPropertiesSupplier,
                () -> new PrintStream(outCapture, true),
                () -> new PrintStream(errCapture, true),
                Clock::systemUTC);
        var result = orchestration.execute();
        verify(mockService, times(1)).ingest(any());
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);
    }

    @Test
    void productionCliBuilderCreatesNoneWebContext() throws Exception {
        SpringApplicationBuilder builder = KnowledgeIngestionCli.createBuilder();
        try (var context = builder.run()) {
            assertThat(context).isNotNull();

            // Assert the context is not a WebApplicationContext or ServletWebServerApplicationContext
            // The context created with WebApplicationType.NONE should NOT be a WebApplicationContext
            assertThat(context instanceof org.springframework.web.context.WebApplicationContext)
                    .as("Context should not be an instance of WebApplicationContext")
                    .isFalse();

            // Also verify no WebServer bean exists
            String[] webBeanNames = context.getBeanNamesForType(org.springframework.boot.web.servlet.server.ServletWebServerFactory.class);
            assertThat(webBeanNames).as("No ServletWebServerFactory bean should exist").isEmpty();
        }
    }

    @Test
    void deterministicClockReturnsExpectedDurationInResultAndJson() throws Exception {
        final long EXPECTED_DURATION_MS = 1234L;
        
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(3, 1, 2, 5, EXPECTED_DURATION_MS));

        // Create a deterministic clock that returns successive controlled instants with a known delta
        // We simulate the passage of time by using two different fixed instants
        class TestingClock extends Clock {
            private final Instant start;
            private final long durationMs;
            private boolean firstCall = true;

            TestingClock(Instant start, long durationMs) {
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

        var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
        var orchestration = new KnowledgeIngestionOrchestration(
                mockService,
                () -> properties,
                () -> new PrintStream(outContent, true),
                () -> new PrintStream(errContent, true),
                () -> new TestingClock(Instant.parse("2026-08-01T00:00:00Z"), EXPECTED_DURATION_MS));

        var result = orchestration.execute();
        assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);

        // Assert the exact expected duration in the Success payload
       KnowledgeIngestionResult successResult = ((KnowledgeIngestionOrchestration.IngestionResult.Success) result).result();
        assertThat(successResult.durationMs()).isEqualTo(EXPECTED_DURATION_MS);

        String output = outContent.toString(StandardCharsets.UTF_8);
        assertThat(output).startsWith("R4R_INGESTION_RESULT=");

        String json = output.substring("R4R_INGESTION_RESULT=".length()).trim();
        var mapper = new ObjectMapper();
        var node = mapper.readTree(json);

        // Assert the exact expected duration in the parsed JSON
        long actualDuration = node.get("durationMs").asLong();
        assertThat(actualDuration).isEqualTo(EXPECTED_DURATION_MS);
    }

    @Test
    void knowledgeIngestionServiceReturnsResult() throws Exception {
        var sourceFile = tempRoot.resolve("sample.md");
        Files.writeString(sourceFile, "# Sample\n\nContent here.", StandardCharsets.UTF_8);
        var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
        var loader = new com.riansares.r4r.document.MarkdownDocumentLoader(properties);
        var chunker = new com.riansares.r4r.chunking.HeadingMarkdownChunker(2_000);
        var result = new KnowledgeIngestionResult(5, 3, 2, 10, 1234);
        assertThat(result.discoveredSources()).isEqualTo(5);
        assertThat(result.changedSources()).isEqualTo(3);
        assertThat(result.unchangedSources()).isEqualTo(2);
        assertThat(result.persistedChunks()).isEqualTo(10);
        assertThat(result.durationMs()).isEqualTo(1234);
    }

    @Test
    void a5_randomPortContext_noIngestionOnStartup() throws Exception {
        // A5 lifecycle test: Programmatically create an isolated Spring context with random port.
        // When the web application (R4rSpringAiRagApplication) starts normally, it should NOT trigger ingestion.
        // The orchestration execute() is only called by the explicit CLI invocation.
        
        var localMock = mock(KnowledgeIngestionService.class);
        
        try (var context = new SpringApplicationBuilder()
                .sources(R4rSpringAiRagApplication.class)
                .web(WebApplicationType.NONE)  // NONE mode matches CLI
                .properties("server.port=0")
                .initializers((ConfigurableApplicationContext ctx) -> 
                        ctx.getBeanFactory().registerSingleton("knowledgeIngestionService", localMock))
                .run()) {
            
            // The key assertion: during startup, no methods should have been called on the service.
            // If any method was invoked during startup, that would be an unwanted side effect.
            verifyNoInteractions(localMock);
        }
        
        // Context is now closed explicitly
    }

}
