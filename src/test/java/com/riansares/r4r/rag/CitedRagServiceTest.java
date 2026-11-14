package com.riansares.r4r.rag;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
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

    private KnowledgeVectorStore knowledgeStore;
    private ChatModel chatModel;
    private CitedRagService service;

    @BeforeEach
    void setUp() {
        knowledgeStore = mock(KnowledgeVectorStore.class);
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
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Combined answer [S1][S2]"));

        RagResult result = service.answer("test question");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(knowledgeStore).search("test question", TEST_TOP_K, TEST_MIN_SCORE);
        verify(chatModel).call(promptCaptor.capture());

        assertThat(promptCaptor.getValue().getContents())
                .containsSubsequence("[S1]\nSource: doc1.md\nScope: Section\nFirst content", "[S2]\nSource: doc2.md\nScope: Other\nSecond content", "Question: test question")
                .contains("/no_think")
                .contains("ABSTAINED", "1–3 concise sentences", "only facts supported");
        assertThat(result.answer()).isEqualTo("Combined answer [S1][S2]");
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
                .thenReturn(chatResponse("Generated answer text [S1]"));

        RagResult result = service.answer("test question");

        assertThat(result.answer()).isEqualTo("Generated answer text [S1]");
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

    @Test
    void englishAnswerUsesSpanishRetrievalAndOriginalQuestionInPrompt() {
        var chunk = new MarkdownChunk("fuente.md", List.of("Baño"), 2, "Reforma del baño");
        when(knowledgeStore.search("¿Cuánto cuesta reformar el baño?", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("The renovation depends on the work. [S1]"));

        var result = service.answer("How much does a bathroom renovation cost?",
                "¿Cuánto cuesta reformar el baño?", "en");
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(knowledgeStore).search("¿Cuánto cuesta reformar el baño?", TEST_TOP_K, TEST_MIN_SCORE);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains("How much does a bathroom renovation cost?", "Reforma del baño", "Respond in English.");
        assertThat(prompt.getValue().getInstructions().getFirst().getText())
                .contains("Write the entire answer in English", "every explanatory list item and technical term", "citation labels unchanged",
                        "Return one JSON object only, with keys status, answer, limitation.");
        assertThat(prompt.getValue().getContents()).doesNotContain("¿Cuánto cuesta reformar el baño?");
        assertThat(prompt.getValue().getInstructions().getLast().getText()).doesNotContain("__R4R_ABSTAIN__");
        assertThat(result.citations()).containsExactly(new Citation("[S1]", "fuente.md", List.of("Baño"), 2));
    }

    @Test
    void englishRetrievalFallsBackToOriginalAndAbstainsWithoutEvidence() {
        when(knowledgeStore.search("Question", TEST_TOP_K, TEST_MIN_SCORE)).thenReturn(List.of());
        var result = service.answer("Question", " ", "en");
        assertThat(result.abstention()).isTrue();
        assertThat(result.citations()).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void retrievalFailurePropagatesInsteadOfBeingReportedAsNoEvidence() {
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenThrow(new IllegalStateException("embedding service offline"));

        assertThatThrownBy(() -> service.answer("test question"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("embedding service offline");
        verifyNoInteractions(chatModel);
    }

    @Test
    void generationFailurePropagatesInsteadOfBeingReportedAsAbstention() {
        when(knowledgeStore.search("test question", TEST_TOP_K, TEST_MIN_SCORE))
                .thenReturn(List.of(new MarkdownChunk("doc.md", List.of(), 0, "Relevant")));
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("inference unavailable"));

        assertThatThrownBy(() -> service.answer("test question"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inference unavailable");
    }

    @Test
    void spanishRequestUsesOriginalQuestionAndSpanishPrompt() {
        var chunk = new MarkdownChunk("doc.md", List.of(), 0, "Contenido");
        when(knowledgeStore.search("Pregunta", TEST_TOP_K, TEST_MIN_SCORE)).thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Respuesta [S1]"));
        service.answer("Pregunta", null, "es");
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains("Question: Pregunta", "Respond in Spanish.");
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
    private RagResult structured(String status, String answer, String limitation, String quote) throws Exception {
        var chunk = new MarkdownChunk("guide.md", List.of("Assessment"), 0,
                "Inspect the **existing installations** before planning any renovation work.");
        when(knowledgeStore.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of(chunk));
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
                "status", status, "answer", answer, "limitation", limitation,
                "support", List.of(java.util.Map.of("label", "[S1]", "quote", quote))));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
        return service.answer("What should I inspect and is my project feasible?");
    }

    @Test void partialRequiresVerbatimSupportAndAnExplicitLimitationInTheSameGeneration() throws Exception {
        var result = structured("PARTIAL", "Inspect the existing installations [S1].",
                "The evidence cannot establish whether this project is feasible.",
                "Inspect the existing installations before planning any renovation work.");
        assertThat(result.status()).isEqualTo(AnswerStatus.PARTIAL);
        assertThat(result.abstention()).isFalse();
        assertThat(result.answer()).contains("cannot establish");
        assertThat(result.citations()).hasSize(1);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test void answeredWithEvidencePreservesItsStatus() throws Exception {
        var result = structured("ANSWERED", "Inspect the existing installations [S1].", "",
                "Inspect the existing installations before planning any renovation work.");
        assertThat(result.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(result.abstention()).isFalse();
    }

    @Test void declaringPartialWithoutEvidenceOrLimitationsCannotBypassValidation() throws Exception {
        for (var fields : List.of(
                List.of("PARTIAL", "A documented fact [S1].", "", "Inspect the existing installations before planning any renovation work."),
                List.of("PARTIAL", "A documented fact [S1].", "Cannot determine feasibility.", "An invented extract unrelated to the source."),
                List.of("PARTIAL", "A documented fact [S99].", "Cannot determine feasibility.", "Inspect the existing installations before planning any renovation work."),
                List.of("PARTIAL", "[S1]", "Cannot determine feasibility.", "Inspect the existing installations before planning any renovation work."),
                List.of("ANSWERED", "A documented fact [S1].", "Cannot determine feasibility.", "Inspect the existing installations before planning any renovation work."))) {
            var result = structured(fields.get(0), fields.get(1), fields.get(2), fields.get(3));
            assertThat(result.status()).isEqualTo(AnswerStatus.ABSTAINED);
            assertThat(result.answer()).isEmpty();
            assertThat(result.citations()).isEmpty();
        }
    }

    @Test void abstainedDiscardsGeneratedTextAndCitations() throws Exception {
        var result = structured("ABSTAINED", "Unsupported text [S1]", "", "irrelevant");
        assertThat(result.status()).isEqualTo(AnswerStatus.ABSTAINED);
        assertThat(result.abstention()).isTrue();
        assertThat(result.answer()).isEmpty();
        assertThat(result.citations()).isEmpty();
    }

    @Test void minimalStructuredAnswerUsesTheExistingCitationValidation() {
        when(knowledgeStore.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of(
                new MarkdownChunk("guide.md", List.of("Checks"), 0, "Inspect the installations before renovation.")));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(
                "{\"status\":\"PARTIAL\",\"answer\":\"Inspect the installations [S1].\","
                + "\"limitation\":\"The evidence cannot establish feasibility.\"}"));
        var result = service.answer("What should I inspect and is it feasible?");
        assertThat(result.status()).isEqualTo(AnswerStatus.PARTIAL);
        assertThat(result.citations()).hasSize(1);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test void aCitedButUnrelatedPartialAnswerIsNotAcceptedAsUsefulEvidence() {
        when(knowledgeStore.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of(
                new MarkdownChunk("es/bathroom.md", List.of("Extractor y luz"), 0, "Ventilación del baño adecuada al uso y normativa aplicables.")));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(
                "{\"status\":\"PARTIAL\",\"answer\":\"Debe revisarse la ventilación [S1].\","
                + "\"limitation\":\"No se documenta cómo iluminar el pasillo.\"}"));
        var result = service.answer("Tengo un pasillo largo y oscuro. ¿Cómo gano luz y aprovecho el espacio?", null, "es");
        assertThat(result.status()).isEqualTo(AnswerStatus.ABSTAINED);
        assertThat(result.answer()).isEmpty();
        assertThat(result.citations()).isEmpty();
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test void aCitedDefinitionCannotLaunderAnAdditionalUncitedTechnicalConclusion() throws Exception {
        var result = structured("ANSWERED",
                "The installations should be inspected [S1]. The entire project is therefore feasible.", "",
                "Inspect the existing installations before planning any renovation work.");
        assertThat(result.status()).isEqualTo(AnswerStatus.ABSTAINED);
        assertThat(result.answer()).isEmpty();
        assertThat(result.citations()).isEmpty();
    }

    @Test void citationsAfterSentencePunctuationRemainCompatible() throws Exception {
        var result = structured("ANSWERED", "Inspect the existing installations. [S1]", "",
                "Inspect the existing installations before planning any renovation work.");
        assertThat(result.status()).isEqualTo(AnswerStatus.ANSWERED);
    }

    @Test void postposedCitationsDoNotHideTheNextUncitedSentence() throws Exception {
        var result = structured("ANSWERED", "Inspect the installations. [S1] The project is therefore feasible.", "",
                "Inspect the existing installations before planning any renovation work.");
        assertThat(result.status()).isEqualTo(AnswerStatus.ABSTAINED);
    }

    @Test void aShortYesOrNoCanReferToTheFollowingCitedExplanation() throws Exception {
        var result = structured("ANSWERED", "Yes. Inspect the existing installations [S1].", "",
                "Inspect the existing installations before planning any renovation work.");
        assertThat(result.status()).isEqualTo(AnswerStatus.ANSWERED);
    }

    @Test void aParagraphFinalCitationMaySupportTwoDocumentedSentences() throws Exception {
        var result = structured("ANSWERED", "Inspect the existing installations. Do this before planning renovation work [S1].", "",
                "Inspect the existing installations before planning any renovation work.");
        assertThat(result.status()).isEqualTo(AnswerStatus.ANSWERED);
    }

}
