package com.riansares.r4r.rag;

import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfiguration {

    @Bean
    CitedRagService citedRagService(
            PgVectorKnowledgeStore knowledgeStore,
            ChatModel chatModel) {
        return new CitedRagService(knowledgeStore, chatModel);
    }
}
