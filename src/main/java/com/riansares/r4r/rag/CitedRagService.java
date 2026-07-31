package com.riansares.r4r.rag;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

@Service
public class CitedRagService {

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant that answers questions based on provided evidence.
            Your answer must be based only on the evidence provided in the context below.
            If the evidence does not contain sufficient information to answer the question, respond with "I cannot answer this question based on the provided evidence."
            Do not make up information or use prior knowledge.
            When citing evidence, use the labels [S1], [S2], etc. exactly as they appear in the context.
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            Context:
            %s
            
            Question: %s
            """;

    private final PgVectorKnowledgeStore knowledgeStore;
    private final ChatModel chatModel;
    private final int topK;
    private final double minScore;

    public CitedRagService(
            PgVectorKnowledgeStore knowledgeStore,
            ChatModel chatModel,
            @Value("${rag.top-k:3}") int topK,
            @Value("${rag.min-score:0.5}") double minScore) {

        this.knowledgeStore = Objects.requireNonNull(knowledgeStore, "knowledgeStore");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        if (!Double.isFinite(minScore)
                || minScore < 0.0
                || minScore > 1.0) {

            throw new IllegalArgumentException(
                    "minScore must be finite and between 0.0 and 1.0");
        }
        this.topK = topK;
        this.minScore = minScore;
    }
/*
    public CitedRagResult answer(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be null or blank");
        }

        List<MarkdownChunk> chunks = knowledgeStore.search(question, topK, minScore);

        if (chunks.isEmpty()) {
            return new CitedRagResult(
                    "I cannot answer this question based on the provided evidence.",
                    true,
                    List.of());
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            MarkdownChunk chunk = chunks.get(i);
            contextBuilder.append("[").append("S").append(i + 1).append("] ")
                    .append(chunk.content())
                    .append("\n\n");
        }

        String userPrompt = String.format(USER_PROMPT_TEMPLATE, contextBuilder, question);
        Prompt prompt = new Prompt(
                new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of()),
                new Prompt(userPrompt));

        ChatResponse response = chatModel.call(prompt);
        String answer = response.getResult().getOutput().getText();

        List<CitedRagResult.Citation> citations = IntStream.range(0, chunks.size())
                .mapToObj(i -> new CitedRagResult.Citation(
                        chunks.get(i).source(),
                        chunks.get(i).headingPath(),
                        i + 1))
                .toList();

        return new CitedRagResult(answer, false, citations);
    }
    */
}
