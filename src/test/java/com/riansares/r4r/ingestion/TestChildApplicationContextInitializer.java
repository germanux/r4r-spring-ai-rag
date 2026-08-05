package com.riansares.r4r.ingestion;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Clock;

/**
 * Test-only ApplicationContextInitializer that replaces the component-scanned
 * KnowledgeIngestionService with a deterministic test double by registering
 * a replacement bean definition after component-scan but before singleton creation.
 * Only active when test.child.process.success system property is present.
 */
public class TestChildApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        if (applicationContext.getEnvironment().containsProperty("test.child.process.success")) {
            // Register a BeanDefinitionRegistryPostProcessor that runs after configuration-class/component-scan
            ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();
            if (beanFactory instanceof BeanDefinitionRegistry registry) {
                registry.registerBeanDefinition(
                    "knowledgeIngestionServiceReplacer",
                    org.springframework.beans.factory.support.BeanDefinitionBuilder
                        .genericBeanDefinition(KnowledgeIngestionServiceReplacer.class)
                        .getBeanDefinition()
                );
            }
        }
    }

    private static void registerInfrastructureDoubles(BeanDefinitionRegistry registry) {
        registry.registerBeanDefinition(
                "jdbcTemplate",
                org.springframework.beans.factory.support.BeanDefinitionBuilder
                        .genericBeanDefinition(
                                org.springframework.jdbc.core.JdbcTemplate.class,
                                () -> org.mockito.Mockito.mock(
                                        org.springframework.jdbc.core.JdbcTemplate.class))
                        .getBeanDefinition()
        );

        registry.registerBeanDefinition(
                "chatModel",
                org.springframework.beans.factory.support.BeanDefinitionBuilder
                        .genericBeanDefinition(
                                org.springframework.ai.chat.model.ChatModel.class,
                                () -> org.mockito.Mockito.mock(
                                        org.springframework.ai.chat.model.ChatModel.class))
                        .getBeanDefinition()
        );
    }

    /**
     * BeanDefinitionRegistryPostProcessor that replaces the knowledgeIngestionService bean definition
     * with a test double implementation. Runs after configuration-class/component-scan registration
     * to ensure bean definitions exist, but before singleton creation.
     */
    public static class KnowledgeIngestionServiceReplacer implements BeanDefinitionRegistryPostProcessor {

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // Not used - we only need registry-level processing
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {


            registerInfrastructureDoubles(registry);

            // Register a no-op VectorStore to satisfy dependencies from PgVectorKnowledgeStore and other RAG components.
            // This allows the child process context to start even though we're not using database/vector-store.
            org.springframework.beans.factory.support.BeanDefinitionBuilder vectorStoreBuilder =
                org.springframework.beans.factory.support.BeanDefinitionBuilder
                    .rootBeanDefinition(NopVectorStore.class);
            registry.registerBeanDefinition("vectorStore", vectorStoreBuilder.getBeanDefinition());

            // Remove the original knowledgeIngestionService bean definition if it exists
            if (registry.containsBeanDefinition("knowledgeIngestionService")) {
                registry.removeBeanDefinition("knowledgeIngestionService");
            }

            // Register the replacement knowledgeIngestionService BeanDefinition
            registerKnowledgeIngestionService(registry);
        }

        private void registerKnowledgeIngestionService(BeanDefinitionRegistry registry) {
            // Get the property to determine success/failure behavior
            String successProp = System.getProperty("test.child.process.success");
            boolean shouldSucceed = !"false".equalsIgnoreCase(successProp);

            // Create a bean definition for our replacement that extends KnowledgeIngestionService
            org.springframework.beans.factory.support.BeanDefinitionBuilder builder =
                org.springframework.beans.factory.support.BeanDefinitionBuilder
                    .rootBeanDefinition(TestKnowledgeIngestionService.class)
                    .addConstructorArgValue(shouldSucceed);

            org.springframework.beans.factory.config.BeanDefinition beanDefinition = builder.getBeanDefinition();

            // Register the replacement as the knowledgeIngestionService bean
            registry.registerBeanDefinition("knowledgeIngestionService", beanDefinition);
        }
    }

    /**
     * No-op VectorStore implementation that satisfies dependencies from RAG components
     * but doesn't actually store anything or require infrastructure.
     */
    public static class NopVectorStore implements VectorStore {

        @Override
        public void add(java.util.List<org.springframework.ai.document.Document> documents) {
            // No-op - we don't need vector storage for ingestion tests
        }

        @Override
        public void delete(java.util.List<String> ids) {
            // No-op
        }

        @Override
        public java.util.List<org.springframework.ai.document.Document> similaritySearch(org.springframework.ai.vectorstore.SearchRequest request) {
            return java.util.Collections.emptyList();
        }

        @Override
        public void delete(
                org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
            // No-op
        }
    }

    /**
     * Deterministic KnowledgeIngestionService used by the child-process tests.
     */
    public static class TestKnowledgeIngestionService extends KnowledgeIngestionService {

        private final boolean shouldSucceed;

        public TestKnowledgeIngestionService(boolean shouldSucceed) {
            super(
                org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class),
                org.mockito.Mockito.mock(com.riansares.r4r.document.MarkdownDocumentLoader.class),
                org.mockito.Mockito.mock(com.riansares.r4r.chunking.HeadingMarkdownChunker.class)
            );
            this.shouldSucceed = shouldSucceed;
        }

        @Override
        public KnowledgeIngestionResult ingest(Clock clock) {
            if (!shouldSucceed) {
                throw new IllegalStateException("Deterministic ingestion failure for child-process test");
            }

            return new KnowledgeIngestionResult(1, 1, 0, 0, 1, 0L);
        }

        @Override
        public void ingest() {
            ingest(Clock.systemUTC());
        }
    }
}
