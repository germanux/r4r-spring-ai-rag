package com.riansares.r4r.rag.api;

import com.riansares.r4r.rag.*;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RagStreamControllerTest {
    private final PgVectorKnowledgeStore store = mock(PgVectorKnowledgeStore.class);
    private final ChatModel pc = mock(ChatModel.class), laptop = mock(ChatModel.class);
    private final GenerationRouter router = new GenerationRouter(
            new GenerationRouter.Target(new GenerationRouter.Provider("PC principal", "http://private-pc", "qwen3:8b"), pc, () -> true),
            new GenerationRouter.Target(new GenerationRouter.Provider("Portátil ASUS", "http://private-laptop", "qwen2.5:7b"), laptop, () -> true));
    private final CitedRagService service = new CitedRagService(store, router, 3, .63);
    private void evidence() {
        when(store.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of(
                new com.riansares.r4r.chunking.MarkdownChunk("doc.md", List.of("Reformas"), 0, "Reforma oficinas.")));
        when(pc.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Reforma oficinas. [S1]")))));
    }
    private MockMvc mvc(RagStreamController controller) { return MockMvcBuilders.standaloneSetup(controller).build(); }
    private org.springframework.test.web.servlet.MvcResult request(MockMvc mvc) throws Exception {
        var result = mvc.perform(post("/api/rag/answers/stream").accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Pregunta\",\"responseLanguage\":\"es\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        result.getAsyncResult(5000);
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
        return result;
    }
    @Test void mvcStreamIncludesOrderedEventsFinalRestContractAndSanitizedProvider() throws Exception {
        evidence();
        try (var controller = new RagStreamController(service, router, Duration.ofSeconds(3))) {
            var result = request(mvc(controller));
            String body = result.getResponse().getContentAsString();
            assertThat(body.indexOf("event:REQUEST_RECEIVED")).isLessThan(body.indexOf("event:RETRIEVAL_STARTED"));
            assertThat(body.indexOf("event:VALIDATION_COMPLETED")).isLessThan(body.indexOf("event:ANSWER_READY"));
            assertThat(body).contains("\"answer\":\"Reforma oficinas. [S1]\"", "\"abstained\":false", "\"citations\":[", "\"model\":\"qwen3:8b\"", "\"metrics\":");
            assertThat(body).doesNotContain("private-pc", "private-laptop", "Reforma oficinas.\\n");
            assertThat(controller.activeSessions()).isZero();
        }
    }
    @Test void emptyRetrievalProducesRealAbstentionAndNoGeneration() throws Exception {
        when(store.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of());
        try (var controller = new RagStreamController(service, router, Duration.ofSeconds(3))) {
            String body = request(mvc(controller)).getResponse().getContentAsString();
            assertThat(body).contains("event:ANSWER_READY", "\"abstained\":true", "\"documentsFound\":0").doesNotContain("event:GENERATION_STARTED", "event:EMBEDDING_STARTED");
            verifyNoInteractions(pc, laptop);
        }
    }
    @Test void allProvidersFailWithSanitizedErrorAndNoAnswerReady() throws Exception {
        evidence();
        when(pc.call(any(Prompt.class))).thenThrow(new IllegalStateException("http://192.168.1.207 secret prompt"));
        when(laptop.call(any(Prompt.class))).thenThrow(new IllegalStateException("credential secret"));
        try (var controller = new RagStreamController(service, router, Duration.ofSeconds(3))) {
            String body = request(mvc(controller)).getResponse().getContentAsString();
            assertThat(body).contains("event:PROVIDER_FAILED", "event:FAILOVER_STARTED", "event:ERROR", "processing_failed")
                    .doesNotContain("event:ANSWER_READY", "192.168", "credential", "secret prompt");
        }
    }
    @Test void disconnectInterruptsWorkerAndPreventsFailover() throws Exception {
        evidence();
        CountDownLatch entered = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        when(pc.call(any(Prompt.class))).thenAnswer(invocation -> {
            entered.countDown();
            try { new CountDownLatch(1).await(); throw new AssertionError(); }
            catch (InterruptedException ex) { interrupted.countDown(); Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
        });
        var emitter = mock(SseEmitter.class);
        AtomicReference<Consumer<Throwable>> disconnected = new AtomicReference<>();
        doAnswer(invocation -> { disconnected.set(invocation.getArgument(0)); return null; }).when(emitter).onError(any());
        try (var controller = new RagStreamController(service, router, Duration.ofSeconds(3)) {
            @Override protected SseEmitter createEmitter() { return emitter; }
        }) {
            controller.stream(new RagQueryRequest("Pregunta", null, "es"));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            disconnected.get().accept(new IOException("client disconnected"));
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
            awaitNoWorkers(controller);
            assertThat(controller.activeSessions()).isZero();
            verifyNoInteractions(laptop);
        }
    }
    @Test void timeoutCancelsWorkerAndReturnsErrorEvent() throws Exception {
        evidence();
        CountDownLatch entered = new CountDownLatch(1);
        when(pc.call(any(Prompt.class))).thenAnswer(invocation -> {
            entered.countDown();
            try { Thread.sleep(5000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            return null;
        });
        try (var controller = new RagStreamController(service, router, Duration.ofMillis(200))) {
            String body = request(mvc(controller)).getResponse().getContentAsString();
            assertThat(body).contains("event:ERROR", "timeout").doesNotContain("event:ANSWER_READY");
            awaitNoWorkers(controller);
            verifyNoInteractions(laptop);
        }
    }
    @Test void invalidRequestDoesNotStartWork() throws Exception {
        try (var controller = new RagStreamController(service, router, Duration.ofSeconds(3))) {
            mvc(controller).perform(post("/api/rag/answers/stream").accept(MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(store, pc, laptop);
        }
    }
    private void awaitNoWorkers(RagStreamController controller) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (controller.activeWorkers() > 0 && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(controller.activeWorkers()).isZero();
    }
}
