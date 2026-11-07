package com.riansares.r4r.rag.api;

import com.riansares.r4r.rag.GenerationRouter;
import com.riansares.r4r.rag.RagProgress;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import com.riansares.r4r.vector.PgVectorTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Tomcat HTTP streaming/CORS. Retrieval and generation are simulated; no live LLM. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.model.embedding=none", "r4r.rag.openai.enabled=false",
        "r4r.api-security.enabled=true", "r4r.api-security.require-origin=true",
        "r4r.rag.warmup.on-startup=false", "r4r.rag.stream.heartbeat-interval=100ms",
        "r4r.api-security.min-interval=0s" })
@Import(PgVectorTestConfiguration.class)
class RagStreamHttpIT {
    @LocalServerPort int port;
    @org.springframework.beans.factory.annotation.Autowired RagStreamController streams;
    @MockBean PgVectorKnowledgeStore store;
    @MockBean GenerationRouter router;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/api/rag/answers/%73tream", "/api/rag/answers;v=1/stream", "/api/rag/%61nswers" })
    void rejectsOriginlessAliasesResolvedByMvc(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(3)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"Pregunta\"}")).build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(path).isEqualTo(403);
            assertThat(response.body()).contains("origin_required");
            verifyNoInteractions(store);
        }
    }

    @Test void realClientDisconnectStopsOwnedWork() throws Exception {
        when(store.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of(
                new com.riansares.r4r.chunking.MarkdownChunk("doc.md", List.of(), 0, "Reformas.")));
        var interrupted = new CountDownLatch(1);
        when(router.call(any(Prompt.class), any(RagProgress.class))).thenAnswer(invocation -> {
            RagProgress progress = invocation.getArgument(1);
            progress.emit("GENERATION_STARTED");
            try { new CountDownLatch(1).await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { interrupted.countDown(); Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            throw new IllegalStateException("Test generation was not cancelled");
        });
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/rag/answers/stream"))
                .timeout(Duration.ofSeconds(5)).header("Origin", "http://127.0.0.1:8000")
                .header("Content-Type", "application/json").header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"Pregunta\",\"responseLanguage\":\"es\"}")).build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).get(3, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && !line.equals("event:GENERATION_STARTED")) { }
                assertThat(line).isEqualTo("event:GENERATION_STARTED");
            } // Close an actual HTTP stream while generation is still blocked.
            assertThat(interrupted.await(3, TimeUnit.SECONDS)).as("Heartbeat detects closed socket and cancels worker").isTrue();
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while ((streams.activeWorkers() > 0 || streams.activeSessions() > 0) && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(streams.activeSessions()).isZero();
        assertThat(streams.activeWorkers()).isZero();
    }

    @Test void deliversEventsOverRealHttpBeforeGenerationCompletes() throws Exception {
        var provider = new GenerationRouter.Provider("PC principal", "http://private-pc", "qwen3:8b");
        when(store.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of(
                new com.riansares.r4r.chunking.MarkdownChunk("oficinas.md", List.of(), 0, "La empresa reforma oficinas.")));
        var release = new CountDownLatch(1);
        when(router.call(any(Prompt.class), any(RagProgress.class))).thenAnswer(invocation -> {
            RagProgress progress = invocation.getArgument(1);
            progress.emit("GENERATION_STARTED", provider, null, null, null);
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test generation not released");
            progress.usedProvider(provider);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Reforma oficinas. [S1]"))));
        });
        when(router.requestExecution()).thenReturn(new GenerationRouter.Execution(provider, false));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/rag/answers/stream"))
                .timeout(Duration.ofSeconds(10)).header("Origin", "http://127.0.0.1:8000")
                .header("Content-Type", "application/json").header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"¿Reforma oficinas?\",\"responseLanguage\":\"es\"}")).build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).get(5, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("text/event-stream");
            assertThat(response.headers().firstValue("access-control-allow-origin")).contains("http://127.0.0.1:8000");
            assertThat(response.headers().firstValue("x-accel-buffering")).contains("no");
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                var early = CompletableFuture.supplyAsync(() -> {
                    StringBuilder text = new StringBuilder();
                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            text.append(line).append('\n');
                            if (line.equals("event:GENERATION_STARTED")) return text.toString();
                        }
                    } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
                    return text.toString();
                }).get(3, TimeUnit.SECONDS);
                assertThat(early).contains("event:REQUEST_RECEIVED", "event:RETRIEVAL_COMPLETED", "event:GENERATION_STARTED")
                        .doesNotContain("event:ANSWER_READY");
                assertThat(release.getCount()).isEqualTo(1); // Proof of delivery before final generation, not just HTTP 200.
                release.countDown();
                var remaining = reader.lines().reduce("", (a, b) -> a + b + "\n");
                assertThat(remaining).contains("event:ANSWER_READY", "\"abstained\":false", "\"label\":\"[S1]\"")
                        .doesNotContain("private-pc", "192.168", "event:ERROR");
            } finally { release.countDown(); }
        } finally { release.countDown(); }
        verify(store).search("¿Reforma oficinas?", 3, .63);
        verify(router).call(any(Prompt.class), any(RagProgress.class));
    }
}
