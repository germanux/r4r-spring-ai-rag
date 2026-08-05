package com.riansares.r4r.ingestion;

import com.riansares.r4r.chunking.HeadingMarkdownChunker;
import com.riansares.r4r.document.MarkdownDocumentLoader;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Test-only initializer that replaces infrastructure-backed beans with deterministic
 * doubles in child-process ingestion tests.
 */
public class TestChildApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String SUCCESS_PROPERTY = "test.child.process.success";
    private static final String REPLACER_BEAN_NAME = "knowledgeIngestionServiceReplacer";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        if (!applicationContext.getEnvironment().containsProperty(SUCCESS_PROPERTY)) {
            return;
        }

        ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            throw new IllegalStateException(
                    "The child-process BeanFactory does not support bean-definition registration");
        }

        BeanDefinition replacer = BeanDefinitionBuilder
                .genericBeanDefinition(KnowledgeIngestionServiceReplacer.class)
                .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                .getBeanDefinition();
        registry.registerBeanDefinition(REPLACER_BEAN_NAME, replacer);
    }

    /**
     * Runs after configuration-class processing and component scanning, but before
     * singleton creation.
     */
    public static class KnowledgeIngestionServiceReplacer
            implements BeanDefinitionRegistryPostProcessor {

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
                throws BeansException {
            registerInfrastructureDoubles(registry);
            replaceKnowledgeIngestionService(registry);
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
                throws BeansException {
            // Registry-level replacement is sufficient for these tests.
        }

        private static void registerInfrastructureDoubles(BeanDefinitionRegistry registry) {
            replaceBeanDefinition(
                    registry,
                    "jdbcTemplate",
                    BeanDefinitionBuilder
                            .genericBeanDefinition(
                                    JdbcTemplate.class,
                                    () -> mock(JdbcTemplate.class))
                            .getBeanDefinition());

            replaceBeanDefinition(
                    registry,
                    "chatModel",
                    BeanDefinitionBuilder
                            .genericBeanDefinition(
                                    ChatModel.class,
                                    () -> mock(ChatModel.class))
                            .getBeanDefinition());

            replaceBeanDefinition(
                    registry,
                    "vectorStore",
                    BeanDefinitionBuilder
                            .rootBeanDefinition(NopVectorStore.class)
                            .getBeanDefinition());
        }

        private static void replaceKnowledgeIngestionService(
                BeanDefinitionRegistry registry) {
            String configuredValue = System.getProperty(SUCCESS_PROPERTY);
            boolean shouldSucceed = !"false".equalsIgnoreCase(configuredValue);

            BeanDefinition replacement = BeanDefinitionBuilder
                    .rootBeanDefinition(TestKnowledgeIngestionService.class)
                    .addConstructorArgValue(shouldSucceed)
                    .getBeanDefinition();

            replaceBeanDefinition(
                    registry,
                    "knowledgeIngestionService",
                    replacement);
        }

        private static void replaceBeanDefinition(
                BeanDefinitionRegistry registry,
                String beanName,
                BeanDefinition replacement) {
            if (registry.containsBeanDefinition(beanName)) {
                registry.removeBeanDefinition(beanName);
            }
            registry.registerBeanDefinition(beanName, replacement);
        }
    }

    /**
     * No-op vector store used to keep RAG components constructible without external
     * infrastructure.
     */
    public static class NopVectorStore implements VectorStore {

        @Override
        public void add(List<Document> documents) {
            // No-op.
        }

        @Override
        public void delete(List<String> ids) {
            // No-op.
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            // No-op.
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return Collections.emptyList();
        }
    }

    /**
     * Deterministic ingestion service used by the child JVM.
     */
    public static class TestKnowledgeIngestionService extends KnowledgeIngestionService {

        private final boolean shouldSucceed;

        public TestKnowledgeIngestionService(boolean shouldSucceed) {
            super(
                    mock(JdbcTemplate.class),
                    mock(MarkdownDocumentLoader.class),
                    mock(HeadingMarkdownChunker.class));
            this.shouldSucceed = shouldSucceed;
        }

        @Override
        public KnowledgeIngestionResult ingest(Clock clock) {
            if (!shouldSucceed) {
                throw new IllegalStateException(
                        "Deterministic ingestion failure for child-process test");
            }

            return new KnowledgeIngestionResult(1, 1, 0, 0, 1, 0L);
        }

        @Override
        public void ingest() {
            ingest(Clock.systemUTC());
        }
    }
}
