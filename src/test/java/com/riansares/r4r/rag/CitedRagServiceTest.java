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
        when(knowledgeStore.search("test question", 5, 0.5))
                .thenReturn(List.of());

        RagResult result = service.answer("test question");

        assertThat(result.abstention()).isTrue();
        assertThat(result.answer()).isEmpty();
        assertThat(result.citations()).isEmpty();

        verify(knowledgeStore).search("test question", 5, 0.5);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void buildsPromptWithLabeledChunksInRetrievalOrder() {
        MarkdownChunk chunk1 = new MarkdownChunk(
                "doc1.md",
                List.of("Section"),
                0,
                "First content");
        MarkdownChunk chunk2 = new MarkdownChunk(
                "doc2.md",
                List.of("Other"),
                1,
                "Second content");

        when(knowledgeStore.search("test question", 5, 0.5))
                .thenReturn(List.of(chunk1, chunk2));

        ChatResponse response = chatResponse("Combined answer");
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        RagResult result = service.answer("test question");

        ArgumentCaptor<Prompt> promptCaptor =
                ArgumentCaptor.forClass(Prompt.class);

        verify(knowledgeStore).search("test question", 5, 0.5);
        verify(chatModel).call(promptCaptor.capture());

        assertThat(promptCaptor.getValue().getContents())
                .isEqualTo("""
                        You are a helpful assistant. Use the following evidence to answer the question.

                        [S1]
                        First content

                        [S2]
                        Second content

                        Question: test question

                        Answer with citations in the format [S1], [S2], etc.:
                        """);

        assertThat(result.answer()).isEqualTo("Combined answer");
        assertThat(result.abstention()).isFalse();
        assertThat(result.citations())
                .extracting(Citation::label)
                .containsExactly("[S1]", "[S2]");
    }

    @Test
    void assignsStableCitationLabelsInRetrievalOrder() {
        MarkdownChunk chunk1 = new MarkdownChunk(
                "doc1.md",
                List.of("Section"),
                0,
                "First content");
        MarkdownChunk chunk2 = new MarkdownChunk(
                "doc2.md",
                List.of("Other"),
                1,
                "Second content");

        when(knowledgeStore.search("test question", 5, 0.5))
                .thenReturn(List.of(chunk1, chunk2));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("Answer"));

        RagResult result = service.answer("test question");

        assertThat(result.citations()).hasSize(2);

        assertThat(result.citations().get(0).label()).isEqualTo("[S1]");
        assertThat(result.citations().get(0).source()).isEqualTo("doc1.md");
        assertThat(result.citations().get(0).headingPath())
                .containsExactly("Section");
        assertThat(result.citations().get(0).ordinal()).isZero();

        assertThat(result.citations().get(1).label()).isEqualTo("[S2]");
        assertThat(result.citations().get(1).source()).isEqualTo("doc2.md");
        assertThat(result.citations().get(1).headingPath())
                .containsExactly("Other");
        assertThat(result.citations().get(1).ordinal()).isEqualTo(1);
    }

    @Test
    void answerIsPropagatedFromChatModel() {
        MarkdownChunk chunk = new MarkdownChunk(
                "doc.md",
                List.of("Section"),
                0,
                "Content");

        when(knowledgeStore.search("test question", 5, 0.5))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("Generated answer text"));

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

        when(knowledgeStore.search("test question", 5, 0.5))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("Answer"));

        RagResult result = service.answer("test question");

        assertThat(result.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.label()).isEqualTo("[S1]");
            assertThat(citation.source()).isEqualTo("guide.md");
            assertThat(citation.headingPath())
                    .containsExactly("Building", "Roof");
            assertThat(citation.ordinal()).isEqualTo(2);
        });
    }

    @Test
    void returnsDefensiveCopyOfCitations() {
        MarkdownChunk chunk = new MarkdownChunk(
                "doc.md",
                List.of("Section"),
                0,
                "Content");

        when(knowledgeStore.search("test question", 5, 0.5))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("Answer"));

        RagResult result = service.answer("test question");

        assertThatThrownBy(
                () -> result.citations().add(mock(Citation.class)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void singleChunkReturnsSingleCitation() {
        MarkdownChunk chunk = new MarkdownChunk(
                "doc.md",
                List.of("Section"),
                0,
                "Content");

        when(knowledgeStore.search("test question", 5, 0.5))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("Answer"));

        RagResult result = service.answer("test question");

        assertThat(result.citations()).singleElement().satisfies(citation ->
                assertThat(citation.label()).isEqualTo("[S1]"));
    }

    @Test
    void multipleChunksGetSequentialLabels() {
        MarkdownChunk chunk1 = new MarkdownChunk(
                "doc1.md",
                List.of("A"),
                0,
                "Content1");
        MarkdownChunk chunk2 = new MarkdownChunk(
                "doc2.md",
                List.of("B"),
                1,
                "Content2");
        MarkdownChunk chunk3 = new MarkdownChunk(
                "doc3.md",
                List.of("C"),
                2,
                "Content3");

        when(knowledgeStore.search("test question", 5, 0.5))
                .thenReturn(List.of(chunk1, chunk2, chunk3));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("Answer"));

        RagResult result = service.answer("test question");

        assertThat(result.citations())
                .extracting(Citation::label)
                .containsExactly("[S1]", "[S2]", "[S3]");
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(
                new Generation(new AssistantMessage(text))));
    }
}
