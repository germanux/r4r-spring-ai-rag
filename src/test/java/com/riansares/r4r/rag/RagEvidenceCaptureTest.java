package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagEvidenceCaptureTest {
    @TempDir Path directory;
    private final KnowledgeVectorStore store = mock(KnowledgeVectorStore.class);
    private final ChatModel model = mock(ChatModel.class);
    private final List<RagEvidenceSnapshot> captures = new ArrayList<>();

    private CitedRagService service(EvidenceRelevanceFilter filter) {
        return new CitedRagService(store, model, 5, .63, filter, captures::add);
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test void preservesExactPromptContentOrderAndUncitedChunksWithoutSearchingAgain() {
        var first = new MarkdownChunk("en/first.md", List.of("Heading", "Child"), 7, "  Full text\n\nwith spacing.\n");
        var second = new MarkdownChunk("en/other.md", List.of(), 11, "Other unabridged text.");
        when(store.search("Kitchen?", 5, .63)).thenReturn(List.of(first, second));
        when(model.call(any(Prompt.class))).thenReturn(response("A useful answer [S1]."));
        var result = service(null).answer("Kitchen?", null, "en");
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        verify(store, times(1)).search("Kitchen?", 5, .63);
        var snapshot = captures.getFirst();
        assertThat(snapshot.question()).isEqualTo("Kitchen?");
        assertThat(snapshot.responseLanguage()).isEqualTo("en");
        assertThat(snapshot.answer()).isEqualTo(result.answer());
        assertThat(snapshot.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(snapshot.generationInvoked()).isTrue();
        assertThat(snapshot.configuration().topK()).isEqualTo(5);
        assertThat(snapshot.configuration().minScore()).isEqualTo(.63);
        assertThat(snapshot.metrics()).containsKeys("embeddingMs", "generationMs", "totalMs");
        assertThat(snapshot.chunks()).extracting(RagEvidenceSnapshot.Evidence::label).containsExactly("[S1]", "[S2]");
        for (int i = 0; i < snapshot.chunks().size(); i++) {
            var evidence = snapshot.chunks().get(i);
            var original = List.of(first, second).get(i);
            assertThat(evidence.content()).isEqualTo(original.content());
            assertThat(evidence.ordinal()).isEqualTo(original.index());
            assertThat(evidence.headingPath()).isEqualTo(original.headingPath());
            assertThat(prompt.getValue().getContents()).contains(evidence.label() + "\nSource: " + evidence.source()
                    + "\nScope: " + String.join(" / ", evidence.headingPath()) + "\n" + evidence.content() + "\n\n");
            assertThat(evidence.cited()).isEqualTo(i == 0);
        }
    }

    @Test void recordsPartialAndRawOutputSeparatelyFromValidatedAnswer() {
        when(store.search(anyString(), eq(5), eq(.63))).thenReturn(List.of(new MarkdownChunk("en/kitchen.md", List.of(), 0, "Kitchen plumbing checks.")));
        String raw = "{\"status\":\"PARTIAL\",\"answer\":\"Check the plumbing [S1].\",\"limitation\":\"Site feasibility is unknown.\"}";
        when(model.call(any(Prompt.class))).thenReturn(response(raw));
        var result = service(null).answer("Kitchen?", null, "en");
        assertThat(result.status()).isEqualTo(AnswerStatus.PARTIAL);
        assertThat(captures.getFirst().generatedOutput()).isEqualTo(raw);
        assertThat(captures.getFirst().answer()).contains("Site feasibility is unknown.");
        assertThat(captures.getFirst().chunks().getFirst().cited()).isTrue();
    }

    @Test void enablingCaptureDoesNotChangeGenerationPromptOrPublicResult() {
        when(store.search(anyString(), eq(5), eq(.63))).thenReturn(List.of(new MarkdownChunk("source.md", List.of("Heading"), 0, "Exact text.")));
        when(model.call(any(Prompt.class))).thenReturn(response("Answer [S1]."));
        var withoutCapture = new CitedRagService(store, model, 5, .63);
        var original = withoutCapture.answer("Question?", null, "en");
        var captured = service(null).answer("Question?", null, "en");
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(0).getContents()).isEqualTo(prompts.getAllValues().get(1).getContents());
        assertThat(captured).isEqualTo(original);
        assertThat(captures).hasSize(1);
    }

    @Test void noEvidenceDoesNotInventContextOrInvokeGeneration() {
        when(store.search(anyString(), eq(5), eq(.63))).thenReturn(List.of());
        service(null).answer("Question?", null, "es");
        var snapshot = captures.getFirst();
        assertThat(snapshot.status()).isEqualTo(AnswerStatus.ABSTAINED);
        assertThat(snapshot.chunks()).isEmpty();
        assertThat(snapshot.generationInvoked()).isFalse();
        assertThat(snapshot.generatedOutput()).isNull();
        assertThat(snapshot.provider()).isNull();
        assertThat(snapshot.outcome()).isEqualTo("no_evidence");
        verifyNoInteractions(model);
    }

    @Test void distinguishesEvidenceRejectedBeforeGeneration() {
        when(store.search(anyString(), eq(5), eq(.63))).thenReturn(List.of(new MarkdownChunk("source.md", List.of(), 0, "Unrelated")));
        var filter = mock(EvidenceRelevanceFilter.class);
        when(filter.select(anyString(), anyList())).thenReturn(new EvidenceRelevanceFilter.Selection(List.of(), false));
        service(filter).answer("Question?", null, "en");
        var snapshot = captures.getFirst();
        assertThat(snapshot.retrievedChunkCount()).isEqualTo(1);
        assertThat(snapshot.chunks()).isEmpty();
        assertThat(snapshot.generationInvoked()).isFalse();
        assertThat(snapshot.outcome()).isEqualTo("evidence_rejected_before_generation");
        verifyNoInteractions(model);
    }

    @Test void capturesOnlyPostFilterContextWithTheLabelsActuallyGivenToTheModel() {
        var discarded = new MarkdownChunk("discarded.md", List.of(), 0, "Excluded text.");
        var delivered = new MarkdownChunk("delivered.md", List.of("Relevant"), 8, "Delivered text.");
        when(store.search(anyString(), eq(5), eq(.63))).thenReturn(List.of(discarded, delivered));
        var filter = mock(EvidenceRelevanceFilter.class);
        when(filter.select(anyString(), anyList()))
                .thenReturn(new EvidenceRelevanceFilter.Selection(List.of(delivered), false));
        when(model.call(any(Prompt.class))).thenReturn(response("Answer [S1]."));
        service(filter).answer("Question?", null, "en");
        var snapshot = captures.getFirst();
        assertThat(snapshot.retrievedChunkCount()).isEqualTo(2);
        assertThat(snapshot.chunks()).hasSize(1);
        assertThat(snapshot.chunks().getFirst().label()).isEqualTo("[S1]");
        assertThat(snapshot.chunks().getFirst().source()).isEqualTo("delivered.md");
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains("[S1]\nSource: delivered.md").doesNotContain("Excluded text.");
    }

    @Test void rejectedGenerationRetainsDeliveredEvidenceAndOriginalReferences() {
        when(store.search(anyString(), eq(5), eq(.63))).thenReturn(List.of(new MarkdownChunk("source.md", List.of(), 0, "Content")));
        String raw = "{\"status\":\"ABSTAINED\",\"answer\":\"Unsupported [S1]\",\"limitation\":\"\"}";
        when(model.call(any(Prompt.class))).thenReturn(response(raw));
        service(null).answer("Question?", null, "en");
        var snapshot = captures.getFirst();
        assertThat(snapshot.answer()).isEmpty();
        assertThat(snapshot.citations()).isEmpty();
        assertThat(snapshot.chunks().getFirst().cited()).isFalse();
        assertThat(snapshot.chunks().getFirst().referencedInGeneratedOutput()).isTrue();
        assertThat(snapshot.generationInvoked()).isTrue();
    }

    @Test void providerBelongsToCurrentGenerationAndNoEvidenceHasNoStaleProvider() {
        when(store.search(anyString(), eq(5), eq(.63))).thenReturn(List.of(new MarkdownChunk("source.md", List.of(), 0, "Content")), List.of());
        when(model.call(any(Prompt.class))).thenReturn(response("Answer [S1]."));
        var router = new GenerationRouter(List.of(new GenerationRouter.Target(
                new GenerationRouter.Provider("test-provider", "http://private-host", "qwen3:8b"), model, () -> true)));
        var service = new CitedRagService(store, router, 5, .63, null, captures::add);
        service.answer("First?", null, "en");
        service.answer("Second?", null, "en");
        assertThat(captures.getFirst().provider()).isEqualTo(new RagEvidenceSnapshot.Provider("test-provider", "qwen3:8b"));
        assertThat(captures.getLast().provider()).isNull();
    }

    @Test void exportsUtf8JsonWithoutOverwritingAndSignalsWriteFailures() throws Exception {
        when(store.search(anyString(), eq(5), eq(.63))).thenReturn(List.of());
        service(null).answer("¿Qué hago?", null, "es");
        var json = new ObjectMapper().findAndRegisterModules();
        var exporter = new RagEvidenceExporter(directory, json);
        exporter.accept(captures.getFirst());
        exporter.accept(captures.getFirst());
        try (var files = Files.list(directory)) {
            var paths = files.toList();
            assertThat(paths).hasSize(2);
            assertThat(json.readTree(paths.getFirst().toFile()).path("question").asText()).isEqualTo("¿Qué hago?");
        }
        var file = Files.createFile(directory.resolve("not-a-directory"));
        assertThatThrownBy(() -> new RagEvidenceExporter(file, json).accept(captures.getFirst()))
                .isInstanceOf(java.io.UncheckedIOException.class);
    }
}
