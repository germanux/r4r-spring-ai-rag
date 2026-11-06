package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded, single-attempt Responses API adapter; never stores prompts or credentials in telemetry. */
public final class OpenAiResponsesModel implements ChatModel {
    private final RestClient client;
    private final String key, model;
    private final Semaphore inFlight = new Semaphore(1);
    public record Usage(long inputTokens, long outputTokens) { }
    private final AtomicReference<Usage> usage = new AtomicReference<>();
    public OpenAiResponsesModel(RestClient client, String key, String model) {
        this.client = client; this.key = key == null ? null : key.strip().replaceAll("^[\"']|[\"']$", ""); this.model = model;
    }
    public Usage lastUsage() { return usage.get(); }
    @Override public ChatResponse call(Prompt prompt) {
        if (key == null || key.isBlank() || !inFlight.tryAcquire()) throw failure();
        try {
            if (prompt.getContents().length() > 32000) throw failure();
            var messages = prompt.getInstructions().stream().map(m -> Map.of(
                    "role", m.getMessageType().getValue(), "content", m.getText())).toList();
            JsonNode response = client.post().uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .body(Map.of("model", model, "input", messages, "store", false,
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
            throw failure();
        } finally { inFlight.release(); }
    }
    private static IllegalStateException failure() { return new IllegalStateException("Remote generation unavailable"); }
}
