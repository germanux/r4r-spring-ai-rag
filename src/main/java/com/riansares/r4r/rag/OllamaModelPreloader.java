package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OllamaModelPreloader {
    private final String baseUrl;
    private final String model;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicReference<String> state = new AtomicReference<>("Cargando modelo");

    public OllamaModelPreloader(@Value("${spring.ai.ollama.base-url:}") String baseUrl,
                                @Value("${spring.ai.ollama.chat.options.model:}") String model,
                                ObjectMapper mapper) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void preloadAfterStartup() {
        if (!started.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                state.set("Cargando modelo");
                String payload = mapper.writeValueAsString(Map.of("model", model, "prompt", "", "stream", false, "keep_alive", "3h"));
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/api/generate"))
                        .timeout(Duration.ofMinutes(30)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                state.set(response.statusCode() >= 200 && response.statusCode() < 300 ? "Modelo cargado" : "Ollama no disponible");
            } catch (Exception ignored) {
                state.set("Ollama no disponible");
            }
        });
    }

    public String model() { return model; }
    public String state() { return state.get(); }
    public void updateLoadedState(boolean loaded) {
        if (loaded) state.set("Modelo cargado");
        else if (started.get() && !"Ollama no disponible".equals(state.get())) state.set("Modelo descargado de memoria");
    }
}
