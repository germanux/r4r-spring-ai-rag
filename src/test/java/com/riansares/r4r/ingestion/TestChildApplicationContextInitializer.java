package com.riansares.r4r.ingestion;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;

import java.time.Clock;

import org.springframework.ai.vectorstore.VectorStore;

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
                    .rootBeanDefinition(TestKnowledgeIngestionService.class);
            
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
        public java.util.Optional<Boolean> delete(java.util.Collection<String> ids) {
            return Optional.of(true);
        }
        
        @Override
        public org.springframework.ai.vectorstore.SearchRequest similaritySearch(org.springframework.ai.vectorstore.SearchRequest request) {
            return org.springframework.ai.vectorstore.SearchRequest.builder()
                .query("")
                .topK(request.topK())
                .similarityThreshold(request.similarityThreshold())
                .build();
        }
    }
    
    /**
     * Test-only subclass of KnowledgeIngestionService that has no dependencies and
     * overrides ingest(Clock) to return deterministic results. This bean definition replaces
     * the production service in test child processes to avoid requiring infrastructure beans.
     */
    public static class TestKnowledgeIngestionService extends KnowledgeIngestionService {
        
        private final boolean shouldSucceed;
        
        // No-arg constructor for Spring BeanDefinitionBuilder rootBeanDefinition
        public TestKnowledgeIngestionService() {
            super(null, null, null);
            this.shouldSucceed = !"false".equalsIgnoreCase(System.getProperty("test.child.process.success", "true"));
        }
        
        private TestKnowledgeIngestionService(boolean shouldSucceed) {
            super(null, null, null);
            this.shouldSucceed = shouldSucceed;
        }
        
        @Override
        public KnowledgeIngestionResult ingest(Clock clock) {
            if (shouldSucceed) {
                return new KnowledgeIngestionResult(0, 0, 0, 0, 0, 123L);
            } else {
                throw new IllegalStateException("Child process failure scenario triggered");
            }
        }

        @Override
        public void ingest() {
            ingest(java.time.Clock.systemUTC());
        }
    }
}
