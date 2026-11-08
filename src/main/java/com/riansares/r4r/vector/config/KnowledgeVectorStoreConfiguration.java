package com.riansares.r4r.vector.config;

import com.riansares.r4r.vector.FailoverKnowledgeVectorStore;
import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(RagVectorProperties.class)
public class KnowledgeVectorStoreConfiguration {
    @Bean
    @Lazy
    PgVectorKnowledgeStore pgVectorKnowledgeStore(VectorStore vectorStore,JdbcTemplate jdbcTemplate) {
        return new PgVectorKnowledgeStore(vectorStore,jdbcTemplate);
    }
    @Bean
    @Primary
    FailoverKnowledgeVectorStore knowledgeVectorStore(
            @Qualifier("pgVectorKnowledgeStore") ObjectProvider<PgVectorKnowledgeStore> postgres,
            @Qualifier("s3VectorsKnowledgeVectorStore") ObjectProvider<KnowledgeVectorStore> s3,
            RagVectorProperties properties,
            @Value("${r4r.rag.vector.s3vectors.enabled:false}") boolean enabled,
            org.springframework.core.env.Environment env) {
        if (!enabled && (properties.failoverEnabled() || "s3vectors".equals(properties.primary())))
            throw new IllegalArgumentException("S3 Vectors must be enabled for the requested vector routing");
        return new FailoverKnowledgeVectorStore(postgres::getObject,enabled ? s3.getObject() : null,
                properties.primary(),properties.failoverEnabled(),env.matchesProfiles("s3-readonly"));
    }
}
