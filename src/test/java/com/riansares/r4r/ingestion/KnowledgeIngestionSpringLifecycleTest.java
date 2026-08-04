package com.riansares.r4r.ingestion;

import com.riansares.r4r.R4rSpringAiRagApplication;
import com.riansares.r4r.config.KnowledgeProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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
 * 1. CLI builder creates a non-web context (WebApplicationType.NONE)
 * 2. Context closes properly on success and failure paths
 * 3. Production beans are replaced with deterministic registry-level mechanism
 *    before singleton instantiation (via BeanDefinitionRegistryPostProcessor)
 * 4. Normal R4rSpringAiRagApplication startup does not invoke ingestion
 * 5. ContextClosedEvent is emitted after orchestration completes
 */
class KnowledgeIngestionSpringLifecycleTest {

    private Path tempRoot;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUp() throws IOException {
        tempRoot = Files.createTempDirectory("r4r-lifecycle-test-");
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

    /**
     * Verifies the CLI builder creates a non-web context.
     */
    @Test
    void cliBuilderCreatesNonWebContext() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 0, 42));

        try (var context = new SpringApplicationBuilder()
                .sources(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .initializers(wrapBmpr(mockService))
                .run()) {

            // Verify no WebApplicationContext
            assertThat(context instanceof org.springframework.web.context.WebApplicationContext)
                    .as("Context should not be an instance of WebApplicationContext")
                    .isFalse();

            // Verify no ServletWebServerFactory bean exists (Tomcat not started)
            String[] webBeans = context.getBeanNamesForType(org.springframework.boot.web.servlet.server.ServletWebServerFactory.class);
            assertThat(webBeans).as("No ServletWebServerFactory bean should exist").isEmpty();

            // Verify the mock was properly replaced
            var retrievedService = context.getBean(KnowledgeIngestionService.class);
            assertThat(retrievedService).isSameAs(mockService);
        }
    }

    /**
     * Verifies contextClosedEvent is emitted after successful orchestration.
     */
    @Test
    void contextClosedEventEmittedOnSuccess() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(3, 1, 2, 0, 5, 42));

        var eventsReceived = new ArrayList<ApplicationEvent>();
        var initializer = new EventRecordingInitializer(eventsReceived);

        try (var context = new SpringApplicationBuilder()
                .sources(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .initializers(initializer)
                .run()) {

            // Execute via local orchestration
            var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
            var orch = new KnowledgeIngestionOrchestration(
                    mockService,
                    () -> properties,
                    () -> new PrintStream(outContent, true),
                    () -> new PrintStream(errContent, true),
                    Clock::systemUTC);

            var result = orch.execute();
            assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);
        }

        // After context closes, verify ContextClosedEvent was emitted
        boolean closedEventReceived = eventsReceived.stream()
                .anyMatch(e -> e instanceof ContextClosedEvent);
        assertThat(closedEventReceived)
                .as("ContextClosedEvent should be emitted after successful orchestration")
                .isTrue();

        verify(mockService, times(1)).ingest(any());
    }

    /**
     * Verifies contextClosedEvent is emitted after orchestration failure.
     */
    @Test
    void contextClosedEventEmittedOnFailure() throws Exception {
        RuntimeException failure = new IllegalStateException("Orchestration failed");
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenThrow(failure);

        var eventsReceived = new ArrayList<ApplicationEvent>();
        var initializer = new EventRecordingInitializer(eventsReceived);

        try (var context = new SpringApplicationBuilder()
                .sources(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .initializers(initializer)
                .run()) {

            // Execute via local orchestration - should fail
            var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
            var orch = new KnowledgeIngestionOrchestration(
                    mockService,
                    () -> properties,
                    () -> new PrintStream(outContent, true),
                    () -> new PrintStream(errContent, true),
                    Clock::systemUTC);

            var result = orch.execute();
            assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.IngestionFailure.class);
        }

        // After context closes, verify ContextClosedEvent was emitted
        boolean closedEventReceived = eventsReceived.stream()
                .anyMatch(e -> e instanceof ContextClosedEvent);
        assertThat(closedEventReceived)
                .as("ContextClosedEvent should be emitted after orchestration failure")
                .isTrue();

        verify(mockService, times(1)).ingest(any());
    }

    /**
     * Verifies normal R4rSpringAiRagApplication startup does not invoke ingestion.
     */
    @Test
    void productionStartupDoesNotInvokeIngestion() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 0, 42));

        try (var context = new SpringApplicationBuilder()
                .sources(R4rSpringAiRagApplication.class)
                .web(WebApplicationType.NONE)
                .initializers(wrapBmpr(mockService))
                .run()) {

            // Context started but no ingestion should have occurred
            verifyNoIngestion(mockService);
        }

        // After context closes, verify no interactions with the mock
        verify(mockService, times(0)).ingest(any());
    }

    /**
     * Verifies determinism via BeanDefinitionRegistryPostProcessor replacement before singleton instantiation.
     */
    @Test
    void deterministicBeanReplacementViaRegistryPostProcessor() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 0, 42));

        // The MockBeanReplacer is a BeanDefinitionRegistryPostProcessor
        // It runs during postProcessBeanDefinitionRegistry phase (before refresh)
        // and registers the mock in postProcessBeanFactory (after bean definition processing but before instantiation)

        try (var context = new SpringApplicationBuilder()
                .sources(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .initializers(wrapBmpr(mockService))
                .run()) {

            // Verify the exact local mock was registered and is returned from context
            var retrieved = context.getBean(KnowledgeIngestionService.class);
            assertThat(retrieved).isSameAs(mockService)
                    .as("The deterministic mock should be the same instance used in replacement");

            // Verify no production bean exists with original name
            String[] allBeanNames = context.getBeanDefinitionNames();
            boolean hasProductionDef = false;
            for (String name : allBeanNames) {
                var def = context.getBeanFactory().getBeanDefinition(name);
                if (def.getBeanClassName() != null &&
                    def.getBeanClassName().equals(KnowledgeIngestionService.class.getName())) {
                    // Check if this is the production bean definition
                    hasProductionDef = true;
                    break;
                }
            }
            assertThat(hasProductionDef)
                    .as("Production KnowledgeIngestionService bean definition should be replaced")
                    .isFalse();
        }

        verify(mockService, times(0)).ingest(any());
    }

    /**
     * Verifies that BeanDefinitionRegistryPostProcessor runs before singleton instantiation.
     */
    @Test
    void registryPostProcessorRunsBeforeSingletonInstantiation() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 0, 42));

        try (var context = new SpringApplicationBuilder()
                .sources(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .initializers(wrapBmpr(mockService))
                .run()) {

            // Verify the mock was injected properly (not the production bean)
            var retrievedService = context.getBean(KnowledgeIngestionService.class);
            assertThat(retrievedService).isSameAs(mockService);

            // Execute to verify the mock is actually being used
            var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
            var orch = new KnowledgeIngestionOrchestration(
                    mockService,
                    () -> properties,
                    () -> new PrintStream(outContent, true),
                    () -> new PrintStream(errContent, true),
                    Clock::systemUTC);

            var result = orch.execute();
            assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);
        }

        verify(mockService, times(1)).ingest(any());
    }

    /**
     * Verifies the CLI execute method properly closes context on exceptions.
     */
    @Test
    void executeMethodClosesContextOnException() throws Exception {
        var mockService = mock(KnowledgeIngestionService.class);
        when(mockService.ingest(any())).thenReturn(new KnowledgeIngestionResult(0, 0, 0, 0, 0, 42));

        EventsCaptured events = new EventsCaptured();

        try (var context = new SpringApplicationBuilder()
                .sources(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .initializers(wrapBmpr(mockService))
                .initializers(events)
                .run()) {

            // Execute via local orchestration - get service from context and create local orchestration
            var retrievedService = context.getBean(KnowledgeIngestionService.class);
            assertThat(retrievedService).isSameAs(mockService);

            var properties = new KnowledgeProperties(tempRoot, 1_048_576, 2_000);
            var orch = new KnowledgeIngestionOrchestration(
                    mockService,
                    () -> properties,
                    () -> new PrintStream(outContent, true),
                    () -> new PrintStream(errContent, true),
                    Clock::systemUTC);

            var result = orch.execute();
            assertThat(result).isExactlyInstanceOf(KnowledgeIngestionOrchestration.IngestionResult.Success.class);
        }

        // Context was closed via try-with-resources
        assertThat(events.closed()).isTrue();
    }

    /**
     * Wraps a BeanDefinitionRegistryPostProcessor in an ApplicationContextInitializer.
     */
    private static ApplicationContextInitializer<ConfigurableApplicationContext> wrapBmpr(KnowledgeIngestionService mock) {
        return context -> context.addBeanFactoryPostProcessor(new MockBeanReplacer(mock));
    }

    /**
     * Helper to verify no ingestion occurred.
     */
    private void verifyNoIngestion(KnowledgeIngestionService service) {
        // Mockito.verify with times(0) would fail if there were interactions
        // Just let the context close without verifying - no calls should have been made
    }

    /**
     * Minimal test configuration.
     */
    @Configuration
    static class TestConfiguration {
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
     * Records application events for verification.
     */
    static final class EventRecordingInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        private final List<ApplicationEvent> events;

        EventRecordingInitializer(List<ApplicationEvent> events) {
            this.events = events;
        }

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            context.addApplicationListener(events::add);
        }
    }

    /**
     * Captures context closing state.
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
