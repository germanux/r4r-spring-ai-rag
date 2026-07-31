package com.riansares.r4r.rag;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

class CitedRagServiceTest {

    private PgVectorKnowledgeStore knowledgeStore;
    private ChatModel chatModel;
    private CitedRagService service;

    @BeforeEach
    void setUp() {
        knowledgeStore = mock(PgVectorKnowledgeStore.class);
        chatModel = mock(ChatModel.class);
        service = new CitedRagService(knowledgeStore, chatModel);
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
        when(knowledgeStore.search(any(), any(Integer.class), any(Double.class)))
                .thenReturn(List.of());

        RagResult result = service.answer("test question");

        assertThat(result.abstention()).isTrue();
        assertThat(result.answer()).isEmpty();
        assertThat(result.citations()).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void buildsPromptWithLabeledChunksInRetrievalOrder() {
        MarkdownChunk chunk1 = new MarkdownChunk(
                "doc1.md", List.of("Section"), 0, "First content");
        MarkdownChunk chunk2 = new MarkdownChunk(
                "doc2.md", List.of("Other"), 1, "Second content");

        when(knowledgeStore.search(any(), any(Integer.class), any(Double.class)))
                .thenReturn(List.of(chunk1, chunk2));

        org.springframework.ai.chat.model.ChatResponse response =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage message =
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn("Combined answer");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(promptCaptor.capture())).thenReturn(response);

        RagResult result = service.answer("test question");

        verify(chatModel).call(promptCaptor.capture());
        
        // Verify retrieval was called with exact parameters: question, top-K 5, min score 0.5
        verify(knowledgeStore).search("test question", 5, 0.5);
        
        String expectedPrompt = "You are a helpful assistant. Use the following evidence to answer the question.\n\n[S1]\nFirst content\n\n[S2]\nSecond content\n\nQuestion: test question\n\nAnswer with citations in the format [S1], [S2], etc.:\n";
        assertThat(promptCaptor.getValue().getContents()).isEqualTo(expectedPrompt);
        
        assertThat(result.answer()).isEqualTo("Combined answer");
        assertThat(result.abstention()).isFalse();
        assertThat(result.citations()).hasSize(2);
        assertThat(result.citations().get(0).label()).isEqualTo("[S1]");
        assertThat(result.citations().get(0).source()).isEqualTo("doc1.md");
        assertThat(result.citations().get(1).label()).isEqualTo("[S2]");
        assertThat(result.citations().get(1).source()).isEqualTo("doc2.md");
    }

    @Test
    void assignsStableCitationLabelsInRetrievalOrder() {
        MarkdownChunk chunk1 = new MarkdownChunk(
                "doc1.md", List.of("Section"), 0, "First content");
        MarkdownChunk chunk2 = new MarkdownChunk(
                "doc2.md", List.of("Other"), 1, "Second content");

        when(knowledgeStore.search(any(), any(Integer.class), any(Double.class)))
                .thenReturn(List.of(chunk1, chunk2));

        org.springframework.ai.chat.model.ChatResponse response =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage message =
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn("Answer");

        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        RagResult result = service.answer("test question");

        assertThat(result.citations()).hasSize(2);
        assertThat(result.citations().get(0).label()).isEqualTo("[S1]");
        assertThat(result.citations().get(0).source()).isEqualTo("doc1.md");
        assertThat(result.citations().get(0).headingPath()).containsExactly("Section");
        assertThat(result.citations().get(0).ordinal()).isEqualTo(0);

        assertThat(result.citations().get(1).label()).isEqualTo("[S2]");
        assertThat(result.citations().get(1).source()).isEqualTo("doc2.md");
        assertThat(result.citations().get(1).headingPath()).containsExactly("Other");
        assertThat(result.citations().get(1).ordinal()).isEqualTo(1);
    }

    @Test
    void answerIsPropagatedFromChatModel() {
        MarkdownChunk chunk = new MarkdownChunk(
                "doc.md", List.of("Section"), 0, "Content");

        when(knowledgeStore.search(any(), any(Integer.class), any(Double.class)))
                .thenReturn(List.of(chunk));

        org.springframework.ai.chat.model.ChatResponse response =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage message =
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn("Generated answer text");

        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        RagResult result = service.answer("test question");

        assertThat(result.answer()).isEqualTo("Generated answer text");
        assertThat(result.abstention()).isFalse();
    }

    @Test
    void citationsPreserveExactMetadataFromRetrievedChunks() {
        MarkdownChunk chunk = new MarkdownChunk(
                "guide.md",
                List.of("Building", "Roof"),
                2,
                "Roof details");

        when(knowledgeStore.search(any(), any(Integer.class), any(Double.class)))
                .thenReturn(List.of(chunk));

        org.springframework.ai.chat.model.ChatResponse response =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage message =
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn("Answer");

        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        RagResult result = service.answer("test question");

        assertThat(result.citations()).hasSize(1);
        Citation citation = result.citations().get(0);
        assertThat(citation.label()).isEqualTo("[S1]");
        assertThat(citation.source()).isEqualTo("guide.md");
        assertThat(citation.headingPath()).containsExactly("Building", "Roof");
        assertThat(citation.ordinal()).isEqualTo(2);
    }

    @Test
    void chatModelIsNeverCalledDuringAbstention() {
        when(knowledgeStore.search(any(), any(Integer.class), any(Double.class)))
                .thenReturn(List.of());

        service.answer("test question");

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void returnsDefensiveCopyOfCitations() {
        MarkdownChunk chunk = new MarkdownChunk(
                "doc.md", List.of("Section"), 0, "Content");

        when(knowledgeStore.search(any(), any(Integer.class), any(Double.class)))
                .thenReturn(List.of(chunk));

        org.springframework.ai.chat.model.ChatResponse response =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage message =
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn("Answer");

        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        RagResult result = service.answer("test question");

        assertThat(result.citations()).isInstanceOf(List.class);
        assertThatThrownBy(() -> result.citations().add(mock(Citation.class)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void singleChunkReturnsSingleCitation() {
        MarkdownChunk chunk = new MarkdownChunk(
                "doc.md", List.of("Section"), 0, "Content");

        when(knowledgeStore.search(any(), any(Integer.class), any(Double.class)))
                .thenReturn(List.of(chunk));

        org.springframework.ai.chat.model.ChatResponse response =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage message =
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn("Answer");

        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        RagResult result = service.answer("test question");

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).label()).isEqualTo("[S1]");
    }

    @Test
    void multipleChunksGetSequentialLabels() {
        MarkdownChunk chunk1 = new MarkdownChunk(
                "doc1.md", List.of("A"), 0, "Content1");
        MarkdownChunk chunk2 = new MarkdownChunk(
                "doc2.md", List.of("B"), 1, "Content2");
        MarkdownChunk chunk3 = new MarkdownChunk(
                "doc3.md", List.of("C"), 2, "Content3");

        when(knowledgeStore.search(any(), any(Integer.class), any(Double.class)))
                .thenReturn(List.of(chunk1, chunk2, chunk3));

        org.springframework.ai.chat.model.ChatResponse response =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage message =
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn("Answer");

        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        RagResult result = service.answer("test question");

        assertThat(result.citations()).hasSize(3);
        assertThat(result.citations().get(0).label()).isEqualTo("[S1]");
        assertThat(result.citations().get(1).label()).isEqualTo("[S2]");
        assertThat(result.citations().get(2).label()).isEqualTo("[S3]");
    }
}
