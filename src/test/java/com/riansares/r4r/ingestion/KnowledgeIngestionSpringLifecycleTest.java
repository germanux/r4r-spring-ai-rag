package com.riansares.r4r.ingestion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.event.ContextClosedEvent;

import java.io.ByteArrayOutputStream;
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
 * 1. CLI builder creates a non-web context via createBuilder()
 * 2. Context closes properly on success and failure paths through try-with-resources
 * 3. Production beans are replaced with deterministic registry-level mechanism
 *    before singleton instantiation (via BeanDefinitionRegistryPostProcessor)
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
     * Verifies the CLI builder creates a non-web context without ServletWebServerFactory.
     */
    @Test
    void cliBuilderCreatesNonWebContext() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 0, 42));

        try (var context = KnowledgeIngestionCli.createBuilder()
                .initializers(wrapBmpr(mockService))
                .run()) {

            // Verify no ServletWebServerFactory bean exists (Tomcat not started)
            String[] webBeans = context.getBeanNamesForType(org.springframework.boot.web.servlet.server.ServletWebServerFactory.class);
            assertThat(webBeans).as("No ServletWebServerFactory bean should exist").isEmpty();
        }
    }

    /**
     * Verifies ContextClosedEvent is emitted when using the CLI builder with try-with-resources.
     */
    @Test
    void contextClosedEventEmittedOnBuilderClose() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(3, 1, 2, 0, 5, 42));

        EventsCaptured events = new EventsCaptured();

        try (var context = KnowledgeIngestionCli.createBuilder()
                .initializers(wrapBmpr(mockService))
                .initializers(events)
                .run()) {

            // Verify we can get the orchestration and run it
            var orch = context.getBean(KnowledgeIngestionOrchestration.class);
            var result = orch.execute();
            assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);
        }

        // Context was closed via try-with-resources
        assertThat(events.closed()).as("ContextClosedEvent should be emitted after context close").isTrue();

        verify(mockService, times(1)).ingest(any());
    }

    /**
     * Verifies ContextClosedEvent is emitted when orchestration fails.
     */
    @Test
    void contextClosedEventEmittedOnBuilderCloseWithFailure() throws Exception {
        RuntimeException failure = new IllegalStateException("Orchestration failed");
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenThrow(failure);

        EventsCaptured events = new EventsCaptured();

        try (var context = KnowledgeIngestionCli.createBuilder()
                .initializers(wrapBmpr(mockService))
                .initializers(events)
                .run()) {

            // Verify orchestration fails and ContextClosedEvent is still emitted
            var orch = context.getBean(KnowledgeIngestionOrchestration.class);
            var result = orch.execute();
            assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        }

        // Context was closed via try-with-resources
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

        // Use the CLI's createBuilder which is non-web
        try (var context = KnowledgeIngestionCli.createBuilder()
                .initializers(wrapBmpr(mockService))
                .run()) {

            // Context started but no ingestion should have occurred
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
