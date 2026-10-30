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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CitedRagServiceCitationsTest {

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
    void assignsStableCitationLabelsInRetrievalOrder() {
        MarkdownChunk chunk1 = new MarkdownChunk("doc1.md", List.of("Section"), 0, "First content");
        MarkdownChunk chunk2 = new MarkdownChunk("doc2.md", List.of("Other"), 1, "Second content");
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of(chunk1, chunk2));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Answer [S1][S2]"));

        RagResult result = service.answer("test question");

        assertThat(result.citations()).hasSize(2);
        assertThat(result.citations().get(0).label()).isEqualTo("[S1]");
        assertThat(result.citations().get(0).source()).isEqualTo("doc1.md");
        assertThat(result.citations().get(0).headingPath()).containsExactly("Section");
        assertThat(result.citations().get(0).ordinal()).isZero();
        assertThat(result.citations().get(1).label()).isEqualTo("[S2]");
        assertThat(result.citations().get(1).source()).isEqualTo("doc2.md");
        assertThat(result.citations().get(1).headingPath()).containsExactly("Other");
        assertThat(result.citations().get(1).ordinal()).isEqualTo(1);
    }

    @Test
    void citationsPreserveExactMetadataFromRetrievedChunks() {
        MarkdownChunk chunk = new MarkdownChunk("guide.md", List.of("Building", "Roof"), 2, "Roof details");
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Answer [S1]"));

        RagResult result = service.answer("test question");

        assertThat(result.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.label()).isEqualTo("[S1]");
            assertThat(citation.source()).isEqualTo("guide.md");
            assertThat(citation.headingPath()).containsExactly("Building", "Roof");
            assertThat(citation.ordinal()).isEqualTo(2);
        });
    }

    @Test
    void returnsDefensiveCopyOfCitations() {
        MarkdownChunk chunk = new MarkdownChunk("doc.md", List.of("Section"), 0, "Content");
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Answer [S1]"));

        RagResult result = service.answer("test question");

        assertThatThrownBy(() -> result.citations().add(mock(Citation.class)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void singleChunkReturnsSingleCitation() {
        MarkdownChunk chunk = new MarkdownChunk("doc.md", List.of("Section"), 0, "Content");
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Answer [S1]"));

        RagResult result = service.answer("test question");

        assertThat(result.citations()).singleElement().satisfies(citation ->
                assertThat(citation.label()).isEqualTo("[S1]"));
    }

    @Test
    void multipleChunksGetSequentialLabels() {
        MarkdownChunk chunk1 = new MarkdownChunk("doc1.md", List.of("A"), 0, "Content1");
        MarkdownChunk chunk2 = new MarkdownChunk("doc2.md", List.of("B"), 1, "Content2");
        MarkdownChunk chunk3 = new MarkdownChunk("doc3.md", List.of("C"), 2, "Content3");
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of(chunk1, chunk2, chunk3));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Answer [S1][S2][S3]"));

        RagResult result = service.answer("test question");

        assertThat(result.citations()).extracting(Citation::label)
                .containsExactly("[S1]", "[S2]", "[S3]");
    }

    @Test
    void returnsOnlyReferencedSourcesAndPreservesTheirOriginalLabels() {
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE)).thenReturn(List.of(
                new MarkdownChunk("a.md", List.of(), 0, "A"),
                new MarkdownChunk("b.md", List.of(), 1, "B"),
                new MarkdownChunk("c.md", List.of(), 2, "C")));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Answer [S3, S1] and [S3]."));
        RagResult result = service.answer("test question");
        assertThat(result.answer()).isEqualTo("Answer [S3][S1] and [S3].");
        assertThat(result.citations()).extracting(Citation::label).containsExactly("[S1]", "[S3]");
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Answer without references."));
        assertThat(service.answer("test question").citations()).isEmpty();
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
