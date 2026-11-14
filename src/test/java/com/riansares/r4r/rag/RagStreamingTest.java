package com.riansares.r4r.rag;

import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Spring AI PgVectorStore pipeline with simulated JDBC/LLMs, not a live LLM integration. */
class RagStreamingTest {
    private final org.springframework.ai.ollama.api.OllamaApi embeddings = mock(org.springframework.ai.ollama.api.OllamaApi.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ChatModel pc = mock(ChatModel.class), laptop = mock(ChatModel.class), remote = mock(ChatModel.class);
    private final List<Map<String, Object>> events = new ArrayList<>();
    private final RagProgress progress = new RagProgress(events::add);

    private CitedRagService service(List<Document> documents, boolean pcAvailable, boolean laptopAvailable) {
        when(embeddings.embed(any())).thenReturn(new org.springframework.ai.ollama.api.OllamaApi.EmbeddingsResponse("nomic-embed-text:latest", List.of(new float[768]), 0L, 0L, 1));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(documents);
        var vector = PgVectorStore.builder(jdbc, new ObservedEmbeddingModel(embeddings, org.springframework.ai.ollama.api.OllamaOptions.builder().model("nomic-embed-text:latest").build()))
                .dimensions(768).initializeSchema(false).build();
        var knowledge = new PgVectorKnowledgeStore(vector, jdbc);
        var router = new GenerationRouter(List.of(
                new GenerationRouter.Target(new GenerationRouter.Provider("PC principal", "http://private-pc", "qwen3:8b"), pc, () -> pcAvailable),
                new GenerationRouter.Target(new GenerationRouter.Provider("Portátil ASUS", "http://private-laptop", "qwen2.5:7b"), laptop, () -> laptopAvailable),
                new GenerationRouter.Target(new GenerationRouter.Provider("OpenAI", "https://api.openai.com/v1", "gpt-6-luna"), remote, () -> true)));
        return new CitedRagService(knowledge, router, 3, .63);
    }
    private List<Document> documents() {
        return List.of(Document.builder().text("La empresa reforma viviendas y oficinas.")
                .metadata(Map.of("source", "reformas.md", "headingPath", List.of("Reformas"), "ordinal", 0)).build());
    }
    private ChatResponse answer() { return new ChatResponse(List.of(new Generation(new AssistantMessage("Reforma viviendas y oficinas. [S1]")))); }
    private List<String> stages() { return events.stream().map(e -> (String)e.get("stage")).toList(); }

    @Test void orderedRealPhasesAndSingleEmbeddingWithHonestMetrics() {
        var service = service(documents(), true, true);
        when(pc.call(any(Prompt.class))).thenReturn(answer());
        progress.emit("REQUEST_RECEIVED");
        var result = service.answer("¿Qué reformas?", null, "es", progress);
        assertThat(result.abstention()).isFalse();
        assertThat(result.citations()).hasSize(1);
        assertThat(stages()).containsExactly("REQUEST_RECEIVED", "RETRIEVAL_STARTED", "EMBEDDING_STARTED", "EMBEDDING_COMPLETED", "RETRIEVAL_COMPLETED", "GENERATION_STARTED", "GENERATION_COMPLETED", "VALIDATION_COMPLETED");
        assertThat(events.get(4).get("documentsFound")).isEqualTo(1);
        assertThat(events.get(5)).containsEntry("provider", "PC principal").containsEntry("model", "qwen3:8b");
        verify(embeddings, times(1)).embed(any());
        verify(jdbc, times(1)).query(anyString(), any(RowMapper.class), any(Object[].class));
        var metrics = progress.metrics();
        assertThat((Double)metrics.get("embeddingMs")).isGreaterThanOrEqualTo(0);
        assertThat((Double)metrics.get("retrievalIncludingEmbeddingMs")).isGreaterThanOrEqualTo((Double)metrics.get("embeddingMs"));
        assertThat((Double)metrics.get("totalMs")).isGreaterThanOrEqualTo((Double)metrics.get("generationMs"));
        assertThat(metrics.get("vectorSearchMs")).isNull();
        assertThat(events.stream().map(e -> (Long)e.get("elapsedMs")).toList()).isSorted();
        assertThat(events.toString()).doesNotContain("private-pc", "private-laptop", "La empresa");
    }

    @Test void noEvidenceAbstainsWithoutInventingGenerationPhases() {
        var result = service(List.of(), true, true).answer("Pregunta", null, "es", progress);
        assertThat(result.abstention()).isTrue();
        assertThat(stages()).containsExactly("RETRIEVAL_STARTED", "EMBEDDING_STARTED", "EMBEDDING_COMPLETED", "RETRIEVAL_COMPLETED", "VALIDATION_COMPLETED");
        assertThat(events.get(3)).containsEntry("documentsFound", 0).containsEntry("message", "no_evidence");
        assertThat(progress.metrics().get("generationMs")).isNull();
        verifyNoInteractions(pc, laptop, remote);
        verify(embeddings).embed(any());
    }

    @Test void pcFailureUsesLaptopWithoutChangingRouterPriority() {
        var service = service(documents(), true, true);
        when(pc.call(any(Prompt.class))).thenThrow(new IllegalStateException("private error"));
        when(laptop.call(any(Prompt.class))).thenReturn(answer());
        service.answer("Pregunta", null, "es", progress);
        assertThat(stages()).containsSubsequence("GENERATION_STARTED", "PROVIDER_FAILED", "FAILOVER_STARTED", "GENERATION_STARTED", "GENERATION_COMPLETED");
        assertThat(events).filteredOn(e -> "FAILOVER_STARTED".equals(e.get("stage"))).singleElement()
                .satisfies(e -> assertThat(e).containsEntry("provider", "Portátil ASUS"));
        assertThat(progress.usedProvider().model()).isEqualTo("qwen2.5:7b");
        verifyNoInteractions(remote);
    }

    @Test void disconnectedPcEmitsFailureButNoGenerationStartForThatPc() {
        var service = service(documents(), false, true);
        when(laptop.call(any(Prompt.class))).thenReturn(answer());
        service.answer("Pregunta", null, "es", progress);
        assertThat(events).filteredOn(e -> "GENERATION_STARTED".equals(e.get("stage"))).singleElement()
                .satisfies(e -> assertThat(e).containsEntry("provider", "Portátil ASUS"));
        assertThat(stages()).containsSubsequence("PROVIDER_FAILED", "FAILOVER_STARTED", "GENERATION_STARTED");
        verifyNoInteractions(pc, remote);
    }

    @Test void allProviderErrorsNeverInventCompletionAndClearEmbeddingScope() {
        var service = service(documents(), true, true);
        for (var model : List.of(pc, laptop, remote)) when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("secret"));
        assertThatThrownBy(() -> service.answer("Pregunta", null, "es", progress)).isInstanceOf(RuntimeException.class);
        assertThat(stages()).filteredOn(s -> s.equals("PROVIDER_FAILED")).hasSize(3);
        assertThat(stages()).doesNotContain("GENERATION_COMPLETED", "VALIDATION_COMPLETED");
        int count = events.size();
        new ObservedEmbeddingModel(embeddings, org.springframework.ai.ollama.api.OllamaOptions.builder().model("nomic-embed-text:latest").build()).embed("Unrelated ingestion");
        assertThat(events).hasSize(count); // The scoped ThreadLocal is removed on the same worker.
    }

    @Test void directBilingualRetrievalNeverEmitsTranslationEvents() {
        var store = mock(KnowledgeVectorStore.class);
        when(store.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of());
        var service = new CitedRagService(store, pc, 3, .63);
        service.answer("Question", "Pregunta externa", "en", progress);
        verify(store).search("Pregunta externa", 3, .63);
        service.answer("Question", null, "en", progress);
        verify(store).search("Question", 3, .63);
        assertThat(stages()).doesNotContain("TRANSLATION_STARTED", "TRANSLATION_COMPLETED");
        verifyNoInteractions(pc);
    }
}
