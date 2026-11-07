package com.riansares.r4r.vector.config;

import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL is the sole vector provider until another provider is explicitly implemented. */
@Configuration(proxyBeanMethods = false)
public class KnowledgeVectorStoreConfiguration {

    @Bean
    PgVectorKnowledgeStore pgVectorKnowledgeStore(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        return new PgVectorKnowledgeStore(vectorStore, jdbcTemplate);
    }
}
