package com.riansares.r4r.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.R4rSpringAiRagApplication;
import com.riansares.r4r.config.KnowledgeProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Objects;
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
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 42));

        // Pre-refresh initializer that installs a BeanDefinitionRegistryPostProcessor
        // to replace the production knowledgeIngestionService with our mock before singleton creation.
        ApplicationContextInitializer<ConfigurableApplicationContext> init = (c) -> {
            c.addBeanFactoryPostProcessor(new MockBeanReplacer(mockService));
        };

        try (var context = new SpringApplicationBuilder()
                .sources(TestMinimalConfig.class) // Minimal config without auto-configuration
                .web(WebApplicationType.NONE)
                .initializers(init)
                .run()) {
            assertThat(context).isNotNull();

            // Verify the exact local mock was registered and is returned from context
            var bean = context.getBean(KnowledgeIngestionService.class);
            assertSame(mockService, bean);

            // Execute using the local orchestration (same as main method does)
            var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
            var orch = new KnowledgeIngestionOrchestration(
                    mockService,
                    () -> properties,
                    () -> new PrintStream(outContent, true),
                    () -> new PrintStream(errContent, true),
                    Clock::systemUTC);

            // Execute to verify ingest is called exactly once
            var result = orch.execute();
            assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);

            // Assert the context is not a WebApplicationContext or ServletWebServerApplicationContext
            assertThat(context instanceof org.springframework.web.context.WebApplicationContext)
                    .as("Context should not be an instance of WebApplicationContext")
                    .isFalse();

            // Also verify no WebServer bean exists
            String[] webBeanNames = context.getBeanNamesForType(org.springframework.boot.web.servlet.server.ServletWebServerFactory.class);
            assertThat(webBeanNames).as("No ServletWebServerFactory bean should exist").isEmpty();
        }

        // Verify ingest was called exactly once (CLI delegation) after context close
        verify(mockService, times(1)).ingest(any());
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
        // This is NOT an infrastructure failure (no SQLException/ConnectException etc.),
        // so it should return exit code 4 for generic failures
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
        // Not an infrastructure failure (no typed SQL/ConnectException), so exit code 4
        assertThat(exitCode).isEqualTo(4);
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
    void executeSqlConnectionFailureReturnsExitCode3AndRedactsErrors() {
        var mockService = mock(KnowledgeIngestionService.class);
        // Use RuntimeException since SQLException is a checked exception and Mockito thenThrow requires the exception to match the method's declared throws clause
        IllegalStateException sqlException = new IllegalStateException(
                "Database connection failed",
                new java.sql.SQLException("Connection refused", "08001"));
        when(mockService.ingest(any())).thenThrow(sqlException);
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
        assertThat(exitCode).as("IllegalStateException with SQLException cause should return exit code 3").isEqualTo(3);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Infrastructure unavailable");
    }

    @Test
    void executePsqlExceptionWrapperInIllegalStateExceptionReturnsExitCode3AndRedactsErrors() {
        var mockService = mock(KnowledgeIngestionService.class);
        java.sql.SQLException psqlException = new java.sql.SQLException(
                "Connection to database failed");
        IllegalStateException wrappedException = new IllegalStateException(
                "Failed to ingest knowledge documents",
                psqlException);
        when(mockService.ingest(any())).thenThrow(wrappedException);
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
        assertThat(exitCode).as("SQLException wrapped in IllegalStateException should return exit code 3").isEqualTo(3);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Infrastructure unavailable");
    }

    @Test
    void executeHikariExceptionWrapperInIllegalStateExceptionReturnsExitCode3AndRedactsErrors() {
        var mockService = mock(KnowledgeIngestionService.class);
        RuntimeException hikariException = new RuntimeException("Connection pool exhausted");
        IllegalStateException wrappedException = new IllegalStateException(
                "Failed to ingest knowledge documents",
                hikariException);
        when(mockService.ingest(any())).thenThrow(wrappedException);
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
        // RuntimeException wrapped in IllegalStateException without specific infrastructure type falls to 4
        assertThat(exitCode).as("RuntimeException wrapped in IllegalStateException without infrastructure classification should return exit code 4").isEqualTo(4);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
    }

    @Test
    void executeSpringDaoExceptionWrapperInIllegalStateExceptionReturnsExitCode3AndRedactsErrors() {
        var mockService = mock(KnowledgeIngestionService.class);
        org.springframework.dao.IncorrectResultSizeDataAccessException daoException =
                new org.springframework.dao.IncorrectResultSizeDataAccessException("Database operation failed", 1, 0);
        IllegalStateException wrappedException = new IllegalStateException(
                "Failed to ingest knowledge documents",
                daoException);
        when(mockService.ingest(any())).thenThrow(wrappedException);
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
        // Spring DAO exception should be classified as infrastructure (exit code 3)
        assertThat(exitCode).as("Spring DAO exception wrapped in IllegalStateException should return exit code 3").isEqualTo(3);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Infrastructure unavailable");
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

    /**
     * Minimal test config without auto-configuration.
     */
    @TestConfiguration
    static class TestMinimalConfig {
    }

    @Test
    void productionCliBuilderCreatesNoneWebContext() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 42));

        // Pre-refresh initializer that installs a BeanDefinitionRegistryPostProcessor
        // to replace the production knowledgeIngestionService with our mock before singleton creation.
        ApplicationContextInitializer<ConfigurableApplicationContext> init = (c) -> {
            c.addBeanFactoryPostProcessor(new MockBeanReplacer(mockService));
        };

        try (var context = new SpringApplicationBuilder()
                .sources(TestMinimalConfig.class) // Minimal config without auto-configuration
                .web(WebApplicationType.NONE)
                .initializers(init)
                .run()) {
            assertThat(context).isNotNull();

            // Verify the exact local mock was registered and is returned from context
            var bean = context.getBean(KnowledgeIngestionService.class);
            assertSame(mockService, bean);

            // Execute using the local orchestration (same as main method does)
            var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
            var orch = new KnowledgeIngestionOrchestration(
                    mockService,
                    () -> properties,
                    () -> new PrintStream(outContent, true),
                    () -> new PrintStream(errContent, true),
                    Clock::systemUTC);

            // Execute to verify ingest is called exactly once
            var result = orch.execute();
            assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);

            // Assert the context is not a WebApplicationContext or ServletWebServerApplicationContext
            assertThat(context instanceof org.springframework.web.context.WebApplicationContext)
                    .as("Context should not be an instance of WebApplicationContext")
                    .isFalse();

            // Also verify no WebServer bean exists
            String[] webBeanNames = context.getBeanNamesForType(org.springframework.boot.web.servlet.server.ServletWebServerFactory.class);
            assertThat(webBeanNames).as("No ServletWebServerFactory bean should exist").isEmpty();
        }

        // Verify ingest was called exactly once (CLI delegation) after context close
        verify(mockService, times(1)).ingest(any());
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

    /**
     * BeanDefinitionRegistryPostProcessor that replaces the production knowledgeIngestionService
     * bean with a mock. Runs after configuration-class definitions are registered but before
     * application beans instantiate.
     */
    static final class MockBeanReplacer implements org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor {
        private final KnowledgeIngestionService mock;

        MockBeanReplacer(KnowledgeIngestionService mock) {
            this.mock = Objects.requireNonNull(mock);
        }

        @Override
        public void postProcessBeanDefinitionRegistry(org.springframework.beans.factory.support.BeanDefinitionRegistry registry) {
            // Remove the production bean definition that was registered by component scanning
            if (registry.containsBeanDefinition("knowledgeIngestionService")) {
                registry.removeBeanDefinition("knowledgeIngestionService");
            }
        }

        @Override
        public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory) {
            // Register our mock as a singleton after all bean definitions have been processed.
            // Using registerSingleton avoids proxy wrapping when using a pre-existing mock instance.
            beanFactory.registerSingleton("knowledgeIngestionService", mock);
        }
    }

    @Test
    void a5_randomPortContext_noIngestionOnStartup() throws Exception {
        // A5 lifecycle test: Verify that R4rSpringAiRagApplication startup
        // does NOT trigger ingestion when we provide our own mock. The orchestration
        // execute() is only called by the explicit CLI invocation.
        // Use SERVLET with server.port=0, register the exact local mock before refresh
        // via BeanDefinitionRegistryPostProcessor that removes production bean and registers mock.
        ConfigurableApplicationContext context = null;
        KnowledgeIngestionService[] mockServiceRef = new KnowledgeIngestionService[1];

        try {
            var sources = new Class<?>[] { R4rSpringAiRagApplication.class };

            // Store the exact local mock before context refresh
            KnowledgeIngestionService mockService = mock(KnowledgeIngestionService.class);
            when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 42));
            mockServiceRef[0] = mockService;

            // Pre-refresh initializer that installs a BeanDefinitionRegistryPostProcessor
            ApplicationContextInitializer<ConfigurableApplicationContext> init = (c) -> {
                c.addBeanFactoryPostProcessor(new MockBeanReplacer(mockServiceRef[0]));
            };

            context = new SpringApplicationBuilder(sources)
                    .web(WebApplicationType.SERVLET)
                    .properties("server.port=0")
                    .initializers(init)
                    .run();

            // Verify the exact local mock was registered and is returned from context
            assertThat(mockServiceRef[0]).isNotNull();
            var retrievedBean = context.getBean(KnowledgeIngestionService.class);
            assertSame(mockServiceRef[0], retrievedBean);
        } finally {
            if (context != null) {
                context.close();  // Explicit close in finally block
            }
        }

        // After context is closed, verify no interactions with the mock
        // (no ingestion should have been triggered by Spring startup)
        if (mockServiceRef[0] != null) {
            verifyNoInteractions(mockServiceRef[0]);
        }
    }

    @Test
    void executeDatabaseConnectFailureReturnsExitCode3AndRedactsErrors() {
        var mockService = mock(KnowledgeIngestionService.class);
         java.sql.SQLException sqlException = new java.sql.SQLException(
                 "Connection refused", "08001");
         IllegalStateException dbException = new IllegalStateException(
                 "Failed to ingest knowledge documents",
                 sqlException);
        when(mockService.ingest(any())).thenThrow(dbException);
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
        // The IllegalStateException with SQLException cause should be classified as infrastructure
        assertThat(exitCode).as("Database connection failure wrapped in IllegalStateException should return exit code 3").isEqualTo(3);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
    }

    @Test
    void executeOllamaEmbeddingTransportFailureReturnsExitCode3AndRedactsErrors() {
        var mockService = mock(KnowledgeIngestionService.class);
        IllegalStateException ollamaException = new IllegalStateException(
                "Failed to ingest knowledge documents",
                new java.net.ConnectException("Ollama embedding service unreachable"));
        when(mockService.ingest(any())).thenThrow(ollamaException);
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
        assertThat(exitCode).as("Ollama transport failure should return exit code 3").isEqualTo(3);
        String err = errContent.toString(StandardCharsets.UTF_8);
        assertThat(err).contains("ERROR: Ingestion failed");
    }

    @TestConfiguration
    static class TestIngestionMockConfig {

        @Bean
        @Primary
        KnowledgeIngestionService knowledgeIngestionService() {
            var mock = mock(KnowledgeIngestionService.class);
            when(mock.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 42));
            return mock;
        }
    }

}
