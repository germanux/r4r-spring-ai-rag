package com.riansares.r4r.rag;

import com.riansares.r4r.config.RagRetrievalProperties;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RagRetrievalProperties.class)
public class RagConfiguration {

    @Bean
    CitedRagService citedRagService(
            PgVectorKnowledgeStore knowledgeStore,
            ChatModel chatModel,
            RagRetrievalProperties properties) {

        return new CitedRagService(
                knowledgeStore,
                chatModel,
                properties.topK(),
                properties.minScore());
    }
}
