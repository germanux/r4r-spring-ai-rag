package com.riansares.r4r.rag;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CitedRagServiceTest {

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
    }

    @Test
    void rejectsNullQuestion() {
        assertThatThrownBy(() -> service.answer(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question");
    }

    @Test
    void rejectsBlankQuestion() {
        assertThatThrownBy(() -> service.answer("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question");
    }

    @Test
    void returnsAbstentionWhenNoChunksRetrieved() {
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of());

        RagResult result = service.answer("test question");

        assertThat(result.abstention()).isTrue();
        assertThat(result.answer()).isEmpty();
        assertThat(result.citations()).isEmpty();
        verify(knowledgeStore).search("test question", TEST_TOP_K, TEST_MIN_SCORE);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void buildsPromptWithLabeledChunksInRetrievalOrder() {
        MarkdownChunk chunk1 = new MarkdownChunk("doc1.md", List.of("Section"), 0, "First content");
        MarkdownChunk chunk2 = new MarkdownChunk("doc2.md", List.of("Other"), 1, "Second content");

        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of(chunk1, chunk2));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Combined answer"));

        RagResult result = service.answer("test question");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(knowledgeStore).search("test question", TEST_TOP_K, TEST_MIN_SCORE);
        verify(chatModel).call(promptCaptor.capture());

        assertThat(promptCaptor.getValue().getContents())
                .containsSubsequence("[S1]\nFirst content", "[S2]\nSecond content", "Question: test question")
                .contains("/no_think")
                .contains("__R4R_ABSTAIN__");
        assertThat(result.answer()).isEqualTo("Combined answer");
        assertThat(result.abstention()).isFalse();
        assertThat(result.citations()).extracting(Citation::label)
                .containsExactly("[S1]", "[S2]");
    }

    @Test
    void answerIsPropagatedFromChatModel() {
        MarkdownChunk chunk = new MarkdownChunk("doc.md", List.of("Section"), 0, "Content");
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("Generated answer text"));

        RagResult result = service.answer("test question");

        assertThat(result.answer()).isEqualTo("Generated answer text");
        assertThat(result.abstention()).isFalse();
    }

    @Test
    void returnsAbstentionWhenModelSaysEvidenceIsInsufficient() {
        MarkdownChunk chunk = new MarkdownChunk("doc.md", List.of("Section"), 0, "Irrelevant content");
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("__R4R_ABSTAIN__"));

        RagResult result = service.answer("test question");

        assertThat(result.abstention()).isTrue();
        assertThat(result.answer()).isEmpty();
        assertThat(result.citations()).isEmpty();
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
