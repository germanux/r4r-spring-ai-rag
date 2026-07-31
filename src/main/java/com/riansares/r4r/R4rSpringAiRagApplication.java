package com.riansares.r4r;

import com.riansares.r4r.config.KnowledgeProperties;
import com.riansares.r4r.ingestion.KnowledgeIngestionOrchestration;
import com.riansares.r4r.ingestion.KnowledgeIngestionService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class R4rSpringAiRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(R4rSpringAiRagApplication.class, args);
    }

    @Bean
    KnowledgeIngestionOrchestration knowledgeIngestionOrchestration(
            KnowledgeIngestionService service,
            KnowledgeProperties properties) {
        return new KnowledgeIngestionOrchestration(service, () -> properties);
    }
}
