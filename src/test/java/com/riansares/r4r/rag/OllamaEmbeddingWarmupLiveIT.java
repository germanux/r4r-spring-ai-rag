package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in: existing Synology model only. No downloads, unloads, text embeddings or database writes. */
@org.junit.jupiter.api.Tag("llm")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "r4r.test.llm.enabled", matches = "true",
        disabledReason = "NOT RUN: real LLM/E2E requires -Pllm-it and explicit test endpoints/credentials")
class OllamaEmbeddingWarmupLiveIT {
    @Test void emptyInputIsAcceptedAndConfiguredModelIsObservedInMemory() throws Exception {
        String url = System.getenv().getOrDefault("RAG_EMBEDDING_LIVE_URL", "");
        assertThat(url.isBlank()).as("RAG_EMBEDDING_LIVE_URL must explicitly select a test Ollama").isFalse();
        String model = System.getenv().getOrDefault("RAG_EMBEDDING_MODEL", "nomic-embed-text:latest");
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2)); factory.setReadTimeout(Duration.ofSeconds(30));
        var api = OllamaApi.builder().baseUrl(url).restClientBuilder(org.springframework.web.client.RestClient.builder().requestFactory(factory)).build();
        assertThat(api.listModels().models()).as("Model must already be installed; never pull it")
                .anyMatch(m -> m.name().equals(model));
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            boolean before = loaded(http, url, model);
            var result = api.embed(new OllamaApi.EmbeddingsRequest(model, List.of(), null, Map.of(), null));
            assertThat(result).isNotNull();
            assertThat(result.embeddings()).isEmpty();
            assertThat(loaded(http, url, model)).isTrue();
            System.out.println("EMBEDDING_WARMUP_LIVE model=" + model + " loadedBefore=" + before + " loadedAfter=true coldTransitionObserved=" + !before);
        }
    }
    private boolean loaded(HttpClient http, String url, String model) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(url.replaceAll("/+$", "") + "/api/ps"))
                .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        for (var item : new ObjectMapper().readTree(response.body()).path("models")) if (model.equals(item.path("name").asText())) return true;
        return false;
    }
}
