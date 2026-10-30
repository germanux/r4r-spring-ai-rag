package com.riansares.r4r.rag.api;

import com.riansares.r4r.config.RagRetrievalProperties;
import com.riansares.r4r.rag.OllamaModelPreloader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagRuntimeController {

    private final RagRetrievalProperties retrieval;
    private final String generationModel;
    private final String embeddingModel;
    private final String embeddingDimensions;
    private final String databaseName;
    private final String ollamaBaseUrl;
    private final ObjectMapper objectMapper;
    private final OllamaModelPreloader preloader;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    public RagRuntimeController(
            RagRetrievalProperties retrieval,
            @Value("${spring.ai.ollama.chat.options.model:No disponible}") String generationModel,
            @Value("${spring.ai.ollama.embedding.options.model:No disponible}") String embeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:No disponible}") String embeddingDimensions,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.ai.ollama.base-url:}") String ollamaBaseUrl,
            ObjectMapper objectMapper,
            OllamaModelPreloader preloader) {
        this.retrieval = retrieval;
        this.generationModel = generationModel;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        this.databaseName = databaseName(datasourceUrl);
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.objectMapper = objectMapper;
        this.preloader = preloader;
    }

    @GetMapping("/runtime")
    public RuntimeInfo runtime() {
        return new RuntimeInfo(generationModel, embeddingModel, embeddingDimensions,
                retrieval.topK(), retrieval.minScore(), System.getProperty("java.version"),
                SpringBootVersion.getVersion(), versionOf(ChatModel.class), databaseName,
                "Docker Compose", loadedModels(), preloader.model(), modelStatus());
    }

    private String modelStatus() {
        if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank()) return "Ollama no disponible";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaBaseUrl.replaceAll("/+$", "") + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return "Ollama no disponible";
            JsonNode models = objectMapper.readTree(response.body()).path("models");
            boolean exists = false;
            for (JsonNode model : models) {
                if (preloader.model().equals(model.path("name").asText()) || preloader.model().equals(model.path("model").asText())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) return "Ollama no disponible";
            boolean loaded = loadedModels().stream().anyMatch(model -> preloader.model().equals(model.name()));
            preloader.updateLoadedState(loaded);
            return loaded ? "Modelo cargado" : preloader.state().equals("Cargando modelo") ? "Cargando modelo" : "Modelo descargado de memoria";
        } catch (Exception ignored) {
            return "Ollama no disponible";
        }
    }

    private List<LoadedModel> loadedModels() {
        if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank()) return List.of();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaBaseUrl.replaceAll("/+$", "") + "/api/ps"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return List.of();
            JsonNode models = objectMapper.readTree(response.body()).path("models");
            List<LoadedModel> result = new ArrayList<>();
            for (JsonNode model : models) {
                result.add(new LoadedModel(model.path("name").asText("unknown"),
                        model.path("details").path("parameter_size").asText("unknown"),
                        model.path("size").asLong(0), model.path("size_vram").asLong(0)));
            }
            return List.copyOf(result);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String versionOf(Class<?> type) {
        String version = type.getPackage().getImplementationVersion();
        return version == null ? "No disponible" : version;
    }

    private static String databaseName(String url) {
        int slash = url.lastIndexOf('/');
        return slash >= 0 && slash + 1 < url.length() ? url.substring(slash + 1) : "PostgreSQL · pgvector";
    }

    public record RuntimeInfo(String generationModel, String embeddingModel, String embeddingDimensions,
                              int topK, double minScore, String javaVersion, String springBootVersion,
                              String springAiVersion, String database, String deployment, List<LoadedModel> loadedModels, String configuredModel, String modelStatus) { }

    public record LoadedModel(String name, String parameterSize, long sizeBytes, long vramBytes) { }
}
