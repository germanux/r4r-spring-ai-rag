package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.config.AuditProviderProperties;
import com.riansares.r4r.config.GenerationProviderProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RagAuditFailoverTest {
    private static final Duration BUDGET = Duration.ofSeconds(3);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private static final String PASS = """
            {"originalStatus":"ANSWERED","recommendedStatus":"ANSWERED","verdict":"PASS",
             "reason":"The cited evidence supports the answer.","note":""}
            """;
    private ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
    private GenerationRouter.Target target(String provider, ChatModel model, java.util.function.BooleanSupplier up) {
        return new GenerationRouter.Target(new GenerationRouter.Provider(provider, "http://localhost", provider + "-model"), model, up);
    }
    private RagEvidenceSnapshot input() {
        return new RagEvidenceSnapshot(Instant.now(), "Question", "en", "Original answer [S1]", AnswerStatus.ANSWERED,
                false, List.of(new Citation("[S1]", "guide.md", List.of(), 0)), null, true, null, "answered", 1,
                List.of(new RagEvidenceSnapshot.Evidence("[S1]", "guide.md", List.of(), 0, "Evidence", true, true)),
                Map.of(), new RagEvidenceSnapshot.Configuration(5, .49, "direct"));
    }
    private RagAuditResult run(RagAuditService audit) throws Exception {
        return audit.start(audit.prepare(input())).get(4, TimeUnit.SECONDS);
    }

    @Test void primaryRecoveryIsPreferredWithoutRestartAndRemoteIsOnlyCalledOnce() throws Exception {
        var up = new AtomicBoolean(false);
        var localCalls = new AtomicInteger(); var remoteCalls = new AtomicInteger();
        try (var audit = new RagAuditService(List.of(
                target("PC", p -> { localCalls.incrementAndGet(); return reply(PASS); }, up::get),
                target("OpenAI", p -> { remoteCalls.incrementAndGet(); return reply(PASS); }, () -> true)), BUDGET, json)) {
            assertThat(run(audit).metadata().provider()).isEqualTo("OpenAI");
            up.set(true);
            assertThat(run(audit).metadata().provider()).isEqualTo("PC");
            assertThat(localCalls).hasValue(1); assertThat(remoteCalls).hasValue(1);
        }
    }

    @Test void semanticResultsAndInvalidJsonNeverInvokeAnotherProvider() throws Exception {
        var fallback = new AtomicInteger();
        for (String output : List.of(PASS, "invalid JSON", PASS.replace("PASS", "INCONCLUSIVE"),
                PASS.replace("PASS", "FAIL").replace("\"recommendedStatus\":\"ANSWERED\"", "\"recommendedStatus\":\"PARTIAL\"")
                        .replace("\"note\":\"\"", "\"note\":\"A conclusion needs verification.\""))) {
            try (var audit = new RagAuditService(List.of(target("PC", p -> reply(output), () -> true),
                    target("OpenAI", p -> { fallback.incrementAndGet(); return reply(PASS); }, () -> true)), BUDGET, json)) {
                var result = run(audit);
                assertThat(result.metadata().provider()).isEqualTo("PC");
                assertThat(result.metadata().outcome()).isEqualTo(output.equals("invalid JSON") ? "invalid_json"
                        : output.contains("INCONCLUSIVE") ? "inconclusive" : "completed");
            }
        }
        assertThat(fallback).hasValue(0);
    }

    @Test void nonRecoverableErrorsDoNotSpendRemoteAndPreserveOriginalStatus() throws Exception {
        try (var audit = new RagAuditService(List.of(target("PC", p -> { throw new IllegalArgumentException("private data"); }, () -> true),
                target("OpenAI", p -> { throw new AssertionError("must not spend"); }, () -> true)), BUDGET, json)) {
            var result = run(audit);
            assertThat(result.effectiveStatus()).isEqualTo(AnswerStatus.ANSWERED);
            assertThat(result.conclusive()).isFalse();
            assertThat(json.writeValueAsString(result)).doesNotContain("private data");
        }
    }

    @Test void timeoutCancellationAndSaturationNeverStartConcurrentOrLateRemoteCalls() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var exited = new CountDownLatch(1); var remoteCalls = new AtomicInteger();
        ChatModel stuck = p -> {
            entered.countDown();
            while (release.getCount() > 0) {
                try { release.await(); } catch (InterruptedException ignored) { }
            }
            exited.countDown(); return reply(PASS);
        };
        try (var audit = new RagAuditService(List.of(target("PC", stuck, () -> true),
                target("OpenAI", p -> { remoteCalls.incrementAndGet(); return reply(PASS); }, () -> true)), Duration.ofMillis(250), json)) {
            var first = audit.start(audit.prepare(input()));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(run(audit).metadata().outcome()).isEqualTo("busy");
            assertThat(first.get(2, TimeUnit.SECONDS).metadata().outcome()).isEqualTo("timeout");
            assertThat(run(audit).metadata().outcome()).isEqualTo("busy");
            release.countDown(); assertThat(exited.await(2, TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); }
        assertThat(remoteCalls).hasValue(0);
    }

    @Test void sequentialCompletedRequestsNeverReportSpuriousBusy() throws Exception {
        try (var audit = new RagAuditService(List.of(target("PC", p -> reply(PASS), () -> true)), BUDGET, json)) {
            for (int i = 0; i < 30; i++) assertThat(run(audit).metadata().outcome()).isEqualTo("completed");
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"probe_timeout", "inference_timeout", "http_503"})
    void springAiTechnicalFailureFailsOverToMockResponsesApiUsingSharedQuota(String scenario) throws Exception {
        var local = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var inferCalls = new AtomicInteger();
        local.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            try {
                boolean tags = exchange.getRequestURI().getPath().equals("/api/tags");
                if (!tags) inferCalls.incrementAndGet();
                if (tags && !scenario.equals("probe_timeout")) {
                    byte[] bytes = "{\"models\":[{\"name\":\"qwen3:8b\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } else {
                    if (!scenario.equals("http_503")) Thread.sleep(300);
                    exchange.sendResponseHeaders(503, -1);
                }
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        local.start();
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(jsonPath("$.model").value("audit-model"))
                .andRespond(withSuccess(json.writeValueAsString(Map.of("status", "completed", "output", List.of(
                        Map.of("type", "message", "role", "assistant", "content", List.of(Map.of("type", "output_text", "text", PASS)))))), MediaType.APPLICATION_JSON));
        var shared = new OpenAiResponsesModel(builder.build(), "TEST_ONLY", "generation-model", 1);
        var props = new AuditProviderProperties(List.of(
                new GenerationProviderProperties.Provider("PC", "http://127.0.0.1:" + local.getAddress().getPort(), "qwen3:8b", Duration.ofMillis(100)),
                new GenerationProviderProperties.Provider("OpenAI", "https://api.openai.com/v1", "audit-model", Duration.ofSeconds(1))),
                Duration.ofMillis(50), Duration.ofMillis(100), BUDGET);
        var generation = new GenerationProviderProperties(List.of(props.providers().getFirst()), Duration.ofMillis(50), Duration.ofMillis(100));
        try (var audit = new RagConfiguration().ragAuditService(props, generation, new OllamaChatProperties(), shared,
                new StaticListableBeanFactory(Map.of("json", json)).getBeanProvider(ObjectMapper.class))) {
            var result = run(audit);
            assertThat(result.conclusive()).isTrue();
            assertThat(result.metadata().provider()).isEqualTo("OpenAI");
            assertThat(result.metadata().model()).isEqualTo("audit-model");
            assertThat(result.metadata().durationMs()).isLessThan(2000);
            assertThatThrownBy(() -> shared.call(new Prompt("Generate")))
                    .isInstanceOfSatisfying(OpenAiResponsesModel.RemoteFailure.class,
                            e -> assertThat(e.reason()).isEqualTo("quota_exhausted"));
        } finally { local.stop(0); }
        server.verify();
        assertThat(inferCalls).hasValue(scenario.equals("probe_timeout") ? 0 : 1);
    }
}
