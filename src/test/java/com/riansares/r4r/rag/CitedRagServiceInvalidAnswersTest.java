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

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
