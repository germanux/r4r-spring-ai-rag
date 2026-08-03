package com.riansares.r4r.ingestion;

import com.riansares.r4r.config.KnowledgeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "r4r.knowledge.root", matchIfMissing = true)
public class IngestionConfiguration {

    @Bean
    KnowledgeIngestionOrchestration knowledgeIngestionOrchestration(
            KnowledgeIngestionService service,
            KnowledgeProperties properties) {
        return new KnowledgeIngestionOrchestration(service, () -> properties);
    }
}
