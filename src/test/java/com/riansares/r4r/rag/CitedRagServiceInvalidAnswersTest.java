package com.riansares.r4r.rag;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CitedRagServiceInvalidAnswersTest {

    private static final int TEST_TOP_K = 3;
    private static final double TEST_MIN_SCORE = 0.42;

    private PgVectorKnowledgeStore knowledgeStore;
    private ChatModel chatModel;
    private CitedRagService service;

    @BeforeEach
    void setUp() {
        knowledgeStore = mock(PgVectorKnowledgeStore.class);
        chatModel = mock(ChatModel.class);
        service = new CitedRagService(knowledgeStore, chatModel, TEST_TOP_K, TEST_MIN_SCORE);
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of(new MarkdownChunk("doc.md", List.of("Section"), 0, "Relevant content")));
    }

    @Test
    void returnsAbstentionForEmptyOrWhitespaceOnlyAnswers() {
        for (String modelAnswer : List.of("", "   ", "\n\t")) {
            when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(modelAnswer));

            RagResult result = service.answer("test question");

            assertThat(result.abstention()).as("Answer: <%s>", modelAnswer).isTrue();
            assertThat(result.answer()).isEmpty();
            assertThat(result.citations()).isEmpty();
        }
    }

    @Test
    void returnsAbstentionForAnswersContainingOnlyCitations() {
        for (String modelAnswer : List.of("[S1]", "[S1] [S2]", "[S1], [S2].", "[S1][S2]", "\n[S1]\n")) {
            when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(modelAnswer));

            RagResult result = service.answer("test question");

            assertThat(result.abstention()).as("Answer: <%s>", modelAnswer).isTrue();
            assertThat(result.answer()).isEmpty();
            assertThat(result.citations()).isEmpty();
        }
    }

    @Test
    void preservesMeaningfulAnswerWithCitation() {
        String modelAnswer = "La solera es la base; el solado es el pavimento final. [S1]";
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(modelAnswer));

        RagResult result = service.answer("test question");

        assertThat(result.abstention()).isFalse();
        assertThat(result.answer()).isEqualTo(modelAnswer);
        assertThat(result.citations()).extracting(Citation::label).containsExactly("[S1]");
    }

    @Test
    void normalizesAndValidatesReferences() {
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE)).thenReturn(List.of(
                new MarkdownChunk("a.md", List.of(), 0, "A"),
                new MarkdownChunk("b.md", List.of(), 0, "B")));
        for (String answer : List.of("Evidence [S1][S2].", "Evidence [S1, S2].", "Evidence [S1,S2].")) {
            when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(answer));
            RagResult result = service.answer("test question");
            assertThat(result.abstention()).isFalse();
            assertThat(result.answer()).isEqualTo("Evidence [S1][S2].");
        }
        for (String answer : List.of("Evidence [S3].", "Evidence [S1, S3].", "Evidence [S0].", "Evidence [S999999999999999999999].", "[S1, S2]")) {
            when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(answer));
            RagResult result = service.answer("test question");
            assertThat(result.abstention()).as(answer).isTrue();
            assertThat(result.answer()).isEmpty();
            assertThat(result.citations()).isEmpty();
        }
    }

    @Test
    void recognizesUnambiguousNaturalLanguageAbstentionsWithoutRejectingWarnings() {
        for (String answer : List.of("No hay información suficiente para responder.",
                "No dispongo de información suficiente sobre el tema. [S1]",
                "La evidencia disponible no permite responder.",
                "I don't have enough information to answer.",
                "There is not enough evidence to answer. [S1]",
                "The provided information is insufficient.")) {
            when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(answer));
            RagResult result = service.answer("test question");
            assertThat(result.abstention()).as(answer).isTrue();
            assertThat(result.answer()).isEmpty();
            assertThat(result.citations()).isEmpty();
        }
        for (String answer : List.of("La solera es una base. No hay información suficiente sobre el precio. [S1]",
                "The slab forms a base. There is not enough information about its cost. [S1]",
                "There is enough information to identify the slab. [S1]")) {
            when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(answer));
            assertThat(service.answer("test question").abstention()).as(answer).isFalse();
        }
    }

    @Test
    void recognizesMultiSentenceAbstentionsWithoutDiscardingUsefulAnswers() {
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE)).thenReturn(List.of(
                new MarkdownChunk("a.md", List.of(), 0, "A"),
                new MarkdownChunk("b.md", List.of(), 0, "B")));
        for (String answer : List.of(
                "The provided evidence does not include specific information about what data the client should provide to request a budget. Therefore, I cannot determine from this context what details are required from the client for requesting a quote. [S1] and [S2] do not address the exact requirements needed from clients at the time of requesting a budget.",
                "The available evidence [S1] does not specify the cost. I cannot determine the price from this context. [S2]",
                "La evidencia disponible [S1] no incluye los datos solicitados. Por tanto, no puedo determinar la respuesta. [S2]",
                "No hay información suficiente para responder. Las fuentes no indican los requisitos. [S1]")) {
            when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(answer));
            RagResult result = service.answer("test question");
            assertThat(result.abstention()).as(answer).isTrue();
            assertThat(result.answer()).isEmpty();
            assertThat(result.citations()).isEmpty();
        }
        for (String answer : List.of(
                "The evidence does not specify the cost. The slab requires a compacted base. [S1]",
                "The evidence does not specify the cost, but the slab requires a compacted base. [S1]",
                "La evidencia no indica el precio. La solera necesita una base compactada. [S1]",
                "No hay información suficiente sobre el precio, pero se requiere una base compactada. [S1]")) {
            when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(answer));
            RagResult result = service.answer("test question");
            assertThat(result.abstention()).as(answer).isFalse();
            assertThat(result.answer()).isEqualTo(answer);
        }
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
