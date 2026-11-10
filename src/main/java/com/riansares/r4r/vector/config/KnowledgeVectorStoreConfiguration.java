package com.riansares.r4r.vector.config;

import com.riansares.r4r.config.NomicRerankProperties;
import com.riansares.r4r.vector.FailoverKnowledgeVectorStore;
import com.riansares.r4r.vector.PostgresAccessGate;
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
@EnableConfigurationProperties({RagVectorProperties.class, NomicRerankProperties.class})
public class KnowledgeVectorStoreConfiguration {
    @Bean
    @Lazy
    PgVectorKnowledgeStore pgVectorKnowledgeStore(VectorStore vectorStore,JdbcTemplate jdbcTemplate,
            NomicRerankProperties ranking) {
        return new PgVectorKnowledgeStore(vectorStore,jdbcTemplate,ranking);
    }
    @Bean
    @Primary
    FailoverKnowledgeVectorStore knowledgeVectorStore(
            @Qualifier("pgVectorKnowledgeStore") ObjectProvider<PgVectorKnowledgeStore> postgres,
            @Qualifier("s3VectorsKnowledgeVectorStore") ObjectProvider<KnowledgeVectorStore> s3,
            RagVectorProperties properties,
            @Value("${r4r.rag.vector.s3vectors.enabled:false}") boolean enabled,
            org.springframework.core.env.Environment env, ObjectProvider<PostgresAccessGate> gate,
            @Qualifier("titanKnowledgeVectorStore") ObjectProvider<KnowledgeVectorStore> titan) {
        if (!enabled && (properties.failoverEnabled() || "s3vectors".equals(properties.primary())))
            throw new IllegalArgumentException("S3 Vectors must be enabled for the requested vector routing");
        KnowledgeVectorStore titanStore = titan.getIfAvailable();
        if (titanStore != null && (!enabled || "pgvector".equals(properties.primary()) && !properties.failoverEnabled()))
            throw new IllegalArgumentException("Titan requires S3 primary or enabled PostgreSQL to S3 failover");
        return new FailoverKnowledgeVectorStore(postgres::getObject,enabled ? s3.getObject() : null,
                properties.primary(),properties.failoverEnabled(),env.matchesProfiles("s3-readonly"),gate.getIfAvailable(),
                titanStore);
    }
}
