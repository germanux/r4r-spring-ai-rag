package com.riansares.r4r.ingestion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.event.ContextClosedEvent;
import com.riansares.r4r.R4rSpringAiRagApplication;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves deterministic non-web Spring lifecycle and no startup ingestion side effect.
 * <p>
 * Tests:
 * 1. createBuilder() returns a non-web builder (no ServletWebServerFactory)
 * 2. execute() properly closes context on success/failure via ContextClosedEvent
 * 3. Registry-level bean replacement before instantiation
 * 4. Normal R4rSpringAiRagApplication startup does not invoke ingestion
 */
class KnowledgeIngestionSpringLifecycleTest {

    private Path tempRoot;

    @BeforeEach
    void setUp() throws IOException {
        tempRoot = Files.createTempDirectory("r4r-lifecycle-test-");
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

    /**
     * Verifies the createBuilder() returns a non-web builder that produces a context without ServletWebServerFactory.
     */
    @Test
    void createBuilderProducesNonWebContextWithoutServletFactory() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 0, 42));

        // Create builder and verify it's non-web by checking the context type
        try (var context = KnowledgeIngestionCli.createBuilder()
                .initializers(wrapBmpr(mockService))
                .run()) {

            // Verify no ServletWebServerFactory bean exists (Tomcat not started)
            String[] webBeans = context.getBeanNamesForType(org.springframework.boot.web.servlet.server.ServletWebServerFactory.class);
            assertThat(webBeans).as("No ServletWebServerFactory bean should exist").isEmpty();

            // Verify the context is not a refreshable web application context
            assertThat(context.getEnvironment().getProperty("server.port")).isNull();
        }
    }

    /**
     * Verifies execute() properly closes context on successful orchestration and emits ContextClosedEvent.
     */
    @Test
    void executeEmitsContextClosedOnSuccess() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(3, 1, 2, 0, 5, 42));

        EventsCaptured events = new EventsCaptured();

        // Configure builder with lifecycle observer and registry-level replacement
        var builder = KnowledgeIngestionCli.createBuilder()
                .initializers(wrapBmpr(mockService))
                .initializers(events);

        // Use reflection to replace createBuilder() temporarily with our configured builder
        // This allows execute() to use the configured builder while keeping the original logic
        ReflectionTestUtils.setField(KnowledgeIngestionCli.class, "builderForTesting", builder);

        try {
            int exitCode = KnowledgeIngestionCli.execute(new String[0]);
            assertThat(exitCode).as("Exit code should be 0 on success").isEqualTo(0);
        } finally {
            // Clear the field for future tests
            ReflectionTestUtils.setField(KnowledgeIngestionCli.class, "builderForTesting", null);
        }

        // Context was closed by execute() and ContextClosedEvent was emitted
        assertThat(events.closed()).as("ContextClosedEvent should be emitted after context close").isTrue();

        verify(mockService, times(1)).ingest(any());
    }

    /**
     * Verifies execute() properly closes context on failing orchestration and emits ContextClosedEvent.
     */
    @Test
    void executeEmitsContextClosedOnFailure() throws Exception {
        RuntimeException failure = new IllegalStateException("Orchestration failed");
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenThrow(failure);

        EventsCaptured events = new EventsCaptured();

        // Configure builder with lifecycle observer and registry-level replacement
        var builder = KnowledgeIngestionCli.createBuilder()
                .initializers(wrapBmpr(mockService))
                .initializers(events);

        // Use reflection to replace createBuilder() temporarily with our configured builder
        ReflectionTestUtils.setField(KnowledgeIngestionCli.class, "builderForTesting", builder);

        try {
            int exitCode = KnowledgeIngestionCli.execute(new String[0]);
            assertThat(exitCode).as("Exit code should be 4 on ingestion failure").isEqualTo(4);
        } finally {
            // Clear the field for future tests
            ReflectionTestUtils.setField(KnowledgeIngestionCli.class, "builderForTesting", null);
        }

        // Context was closed by execute() and ContextClosedEvent was emitted
        assertThat(events.closed()).as("ContextClosedEvent should be emitted after context close with failure").isTrue();

        verify(mockService, times(1)).ingest(any());
    }

    /**
     * Verifies normal R4rSpringAiRagApplication startup does not invoke ingestion.
     */
    @Test
    void productionStartupDoesNotInvokeIngestion() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 0, 42));

        // Use SpringApplicationBuilder directly to start R4rSpringAiRagApplication
        var builder = new SpringApplicationBuilder(R4rSpringAiRagApplication.class)
                .web(WebApplicationType.NONE)
                .initializers(wrapBmpr(mockService));

        try (var context = builder.run()) {
            // Context started but no ingestion should have occurred automatically
        }

        verify(mockService, times(0)).ingest(any());
    }

    /**
     * Wraps a BeanDefinitionRegistryPostProcessor in an ApplicationContextInitializer.
     */
    private static ApplicationContextInitializer<ConfigurableApplicationContext> wrapBmpr(KnowledgeIngestionService mock) {
        return context -> context.addBeanFactoryPostProcessor(new MockBeanReplacer(mock));
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

    /**
     * Captures context closing state via ContextClosedEvent.
     */
    static final class EventsCaptured implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        private boolean closed = false;

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            context.addApplicationListener(event -> {
                if (event instanceof ContextClosedEvent) {
                    closed = true;
                }
            });
        }

        boolean closed() {
            return closed;
        }
    }
}
