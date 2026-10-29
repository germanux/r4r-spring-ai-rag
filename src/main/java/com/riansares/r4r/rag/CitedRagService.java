package com.riansares.r4r.rag;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Cited RAG service that retrieves evidence and generates answers with citations.
 */
public class CitedRagService {


    private final PgVectorKnowledgeStore knowledgeStore;
    private final ChatModel chatModel;
    private final int retrievalTopK;
    private final double minScore;
    private static final String ABSTAIN_TOKEN = "__R4R_ABSTAIN__";
    private static final Pattern ONLY_CITATIONS =
            Pattern.compile("^(?:\\[S\\d+\\][\\s,;:.]*)+$");
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

        Prompt prompt = buildPrompt(  question  + "\n/no_think", chunks);
        ChatResponse response = chatModel.call(prompt);
        String answer = extractAnswer(response);

        if (answer == null
                || answer.isBlank()
                || answer.trim().matches("(?i)^_*R4R_ABSTAIN_*$")
                || answer.contains(ABSTAIN_TOKEN)
                || ONLY_CITATIONS.matcher(answer.trim()).matches()) {
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

        promptText.append("You are a helpful assistant. Use the following evidence to answer the question.\n\n*****\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            MarkdownChunk chunk = chunks.get(i);
            String label = "[S" + (i + 1) + "]";
            promptText.append(label).append("\n");
            promptText.append(chunk.content()).append("\n\n");
        }

        promptText.append("*****\nQuestion: ").append(question).append("\n\n");
        promptText.append("""
        *****
        Answer the user's question using only the supplied evidence.
        Respond in the same language as the question, using 2–5 clear, concise sentences.
        Include relevant citations [S1], [S2], etc. after the explanation, never as the entire answer.
    
        If the evidence does not support an answer, respond with exactly:
        __R4R_ABSTAIN__
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
