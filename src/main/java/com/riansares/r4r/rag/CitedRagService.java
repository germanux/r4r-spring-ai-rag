package com.riansares.r4r.rag;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Cited RAG service that retrieves evidence and generates answers with citations.
 */
public class CitedRagService {


    private final PgVectorKnowledgeStore knowledgeStore;
    private final ChatModel chatModel;
    private final int retrievalTopK;
    private final double minScore;
    private static final String ABSTAIN_TOKEN = "__R4R_ABSTAIN__";

    /**
     * Creates a new cited RAG service.
     *
     * @param knowledgeStore the vector store for evidence retrieval
     * @param chatModel      the chat model for answer generation
     */
    public CitedRagService(
            PgVectorKnowledgeStore knowledgeStore,
            ChatModel chatModel,
            int retrievalTopK,
            double minScore) {
        this.knowledgeStore = Objects.requireNonNull(knowledgeStore, "knowledgeStore");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.retrievalTopK = retrievalTopK;
        this.minScore = minScore;
    }

    /**
     * Processes a question and returns a cited answer.
     *
     * @param question the user's question
     * @return the result with answer, abstention flag, and citations
     */
    public RagResult answer(String question) {
        validateQuestion(question);

        List<MarkdownChunk> chunks = retrieveEvidence(question);

        if (chunks.isEmpty()) {
            return RagResult.abstain();
        }

        Prompt prompt = buildPrompt(question, chunks);
        ChatResponse response = chatModel.call(prompt);
        String answer = extractAnswer(response);
        if (ABSTAIN_TOKEN.equals(answer.trim())) {
            return RagResult.abstain();
        }
        List<Citation> citations = buildCitations(chunks);

        return RagResult.ofAnswer(answer, citations);
    }

    private void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
    }

    private List<MarkdownChunk> retrieveEvidence(String question) {
        return knowledgeStore.search(
                question,
                retrievalTopK,
                minScore);
    }

    private Prompt buildPrompt(String question, List<MarkdownChunk> chunks) {
        StringBuilder promptText = new StringBuilder();

        promptText.append("You are a helpful assistant. Use the following evidence to answer the question.\n\n");

        for (int i = 0; i < chunks.size(); i++) {
            MarkdownChunk chunk = chunks.get(i);
            String label = "[S" + (i + 1) + "]";
            promptText.append(label).append("\n");
            promptText.append(chunk.content()).append("\n\n");
        }

        promptText.append("Question: ").append(question).append("\n\n");
        promptText.append("""
        Answer using only the supplied evidence.
        If the evidence is insufficient or unrelated to the question,
        answer exactly:
        __R4R_ABSTAIN__

        Otherwise answer with citations in the format [S1], [S2], etc.:
        """);

        return new Prompt(promptText.toString());
    }

    private String extractAnswer(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("Chat model returned null response");
        }
        return response.getResult().getOutput().getText();
    }

    private List<Citation> buildCitations(List<MarkdownChunk> chunks) {
        List<Citation> citations = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            MarkdownChunk chunk = chunks.get(i);
            String label = "[S" + (i + 1) + "]";
            citations.add(Citation.fromMarkdownChunk(label, chunk));
        }
        return List.copyOf(citations);
    }
}
