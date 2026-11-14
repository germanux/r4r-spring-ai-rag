package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded, single-attempt Responses API adapter; never stores prompts or credentials in telemetry. */
public final class OpenAiResponsesModel implements ChatModel {
    private final RestClient client;
    private final String key, model;
    private final int maxCallsPerDay;
    private final Semaphore inFlight = new Semaphore(1);
    // One call at a time: guarded by inFlight, reset on UTC date rollover.
    private LocalDate callDay = LocalDate.now(ZoneOffset.UTC);
    private int callsOnDay;
    // Fast rejection only; the authoritative reservation remains guarded by inFlight.
    private volatile LocalDate exhaustedDay;
    public record Usage(long inputTokens, long outputTokens) { }
    private final AtomicReference<Usage> usage = new AtomicReference<>();
    public OpenAiResponsesModel(RestClient client, String key, String model) {
        this(client, key, model, 20);
    }
    public OpenAiResponsesModel(RestClient client, String key, String model, int maxCallsPerDay) {
        if (maxCallsPerDay < 1) throw new IllegalArgumentException("OpenAI daily call limit must be positive");
        this.client = client; this.key = key == null ? null : key.strip().replaceAll("^[\"']|[\"']$", ""); this.model = model;
        this.maxCallsPerDay = maxCallsPerDay;
    }
    public Usage lastUsage() { return usage.get(); }
    /** Audit models share this exact client's quota and in-flight reservation with generation. */
    public ChatModel forModel(String selectedModel) {
        if (selectedModel == null || selectedModel.isBlank()) throw new IllegalArgumentException("Model required");
        return prompt -> call(prompt, selectedModel);
    }
    @Override public ChatResponse call(Prompt prompt) { return call(prompt, model); }
    private ChatResponse call(Prompt prompt, String selectedModel) {
        if (LocalDate.now(ZoneOffset.UTC).equals(exhaustedDay)) throw new RemoteFailure("quota_exhausted");
        if (key == null || key.isBlank() || !inFlight.tryAcquire()) throw failure();
        try {
            if (prompt.getContents().length() > 32000) throw failure();
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            if (!today.equals(callDay)) { callDay = today; callsOnDay = 0; }
            if (callsOnDay >= maxCallsPerDay) {
                org.slf4j.LoggerFactory.getLogger(OpenAiResponsesModel.class)
                        .warn("Remote generation stopped: daily API call limit reached");
                throw new RemoteFailure("quota_exhausted");
            }
            // Count attempts, including unsuccessful HTTP responses, to bound spending.
            callsOnDay++;
            if (callsOnDay >= maxCallsPerDay) exhaustedDay = today;
            var messages = prompt.getInstructions().stream().map(m -> Map.of(
                    "role", m.getMessageType().getValue(), "content", m.getText())).toList();
            JsonNode response = client.post().uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .body(Map.of("model", selectedModel, "input", messages, "store", false,
                            "max_output_tokens", 2048)).retrieve().body(JsonNode.class);
            if (response == null) throw failure();
            usage.set(new Usage(response.path("usage").path("input_tokens").asLong(),
                    response.path("usage").path("output_tokens").asLong()));
            org.slf4j.LoggerFactory.getLogger(OpenAiResponsesModel.class).info(
                    "Remote generation usage inputTokens={} outputTokens={}", usage.get().inputTokens(), usage.get().outputTokens());
            if (!"completed".equals(response.path("status").asText())) throw failure();
            StringBuilder text = new StringBuilder();
            for (var item : response.path("output")) {
                if (!"message".equals(item.path("type").asText()) || !"assistant".equals(item.path("role").asText())) continue;
                for (var part : item.path("content")) if ("output_text".equals(part.path("type").asText()))
                    text.append(part.path("text").asText());
            }
            if (text.toString().isBlank()) throw failure();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text.toString()))));
        } catch (RuntimeException ex) {
            // Do not propagate provider bodies, headers or causes containing credentials/content.
            if (ex instanceof RemoteFailure safe) throw safe;
            throw new RemoteFailure(GenerationRouter.failureReason(ex));
        } finally { inFlight.release(); }
    }
    public static final class RemoteFailure extends IllegalStateException {
        private final String reason;
        private RemoteFailure(String reason) {
            super("Remote generation unavailable");
            this.reason = reason;
        }
        public String reason() { return reason; }
    }
    private static RemoteFailure failure() { return new RemoteFailure("provider_unavailable"); }
}
