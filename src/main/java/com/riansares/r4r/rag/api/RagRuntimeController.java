package com.riansares.r4r.rag.api;

import com.riansares.r4r.config.RagRetrievalProperties;
import com.riansares.r4r.rag.OllamaModelPreloader;
import com.riansares.r4r.rag.GenerationRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final GenerationRouter router;
    private final HttpClient httpClient;

    @Autowired
    public RagRuntimeController(
            RagRetrievalProperties retrieval,
            @Value("${spring.ai.ollama.chat.options.model:No disponible}") String generationModel,
            @Value("${spring.ai.ollama.embedding.options.model:No disponible}") String embeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:No disponible}") String embeddingDimensions,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.ai.ollama.base-url:}") String ollamaBaseUrl,
            ObjectMapper objectMapper,
            OllamaModelPreloader preloader, GenerationRouter router) {
        this(retrieval, generationModel, embeddingModel, embeddingDimensions, datasourceUrl, ollamaBaseUrl,
                objectMapper, preloader, router, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
    }

    RagRuntimeController(RagRetrievalProperties retrieval, String generationModel, String embeddingModel,
                         String embeddingDimensions, String datasourceUrl, String ollamaBaseUrl,
                         ObjectMapper objectMapper, OllamaModelPreloader preloader, GenerationRouter router, HttpClient httpClient) {
        this.httpClient = httpClient;
        this.retrieval = retrieval;
        this.generationModel = generationModel;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        this.databaseName = databaseName(datasourceUrl);
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.objectMapper = objectMapper;
        this.preloader = preloader;
        this.router = router;
    }

    @GetMapping("/runtime")
    public RuntimeInfo runtime() {
        GenerationRouter.Execution execution = router.execution();
        GenerationRouter.Provider last = execution.provider();
        GenerationRouter.Provider selected = last == null ? router.preferred() : last;
        MemoryState memory = execution.failed() ? new MemoryState(false, List.of()) : loadedModels(selected.baseUrl());
        boolean loaded = memory.models().stream().anyMatch(m -> canonical(selected.model()).equals(canonical(m.name())));
        String status = !memory.available() ? "Ollama no disponible" : loaded ? "Modelo cargado"
                : last == null && "Cargando modelo".equals(preloader.state()) ? "Cargando modelo" : "Modelo descargado de memoria";
        return new RuntimeInfo(execution.failed() ? null : selected.model(), embeddingModel, embeddingDimensions,
                retrieval.topK(), retrieval.minScore(), System.getProperty("java.version"),
                SpringBootVersion.getVersion(), versionOf(ChatModel.class), databaseName,
                "Docker Compose", memory.models(), router.preferred().model(), status,
                execution.failed() ? null : InferenceHost.identify(selected.baseUrl(), selected.model()), providerInfo(router.preferred()),
                last == null ? null : providerInfo(last), execution.failed() ? "failed" : last == null ? "idle" : "success");
    }

    private static String canonical(String name) { return name.contains(":") ? name : name + ":latest"; }
    private record MemoryState(boolean available, List<LoadedModel> models) { }

    private MemoryState loadedModels(String ollamaBaseUrl) {
        if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank()) return new MemoryState(false, List.of());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaBaseUrl.replaceAll("/+$", "") + "/api/ps"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return new MemoryState(false, List.of());
            JsonNode models = objectMapper.readTree(response.body()).path("models");
            List<LoadedModel> result = new ArrayList<>();
            for (JsonNode model : models) {
                result.add(new LoadedModel(model.path("name").asText("unknown"),
                        model.path("details").path("parameter_size").asText("unknown"),
                        model.path("size").asLong(0), model.path("size_vram").asLong(0)));
            }
            return new MemoryState(true, List.copyOf(result));
        } catch (Exception ignored) {
            return new MemoryState(false, List.of());
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

    private ProviderInfo providerInfo(GenerationRouter.Provider provider) {
        return new ProviderInfo(provider.name(), provider.model(), InferenceHost.identify(provider.baseUrl(), provider.model()));
    }
    public record ProviderInfo(String name, String model, InferenceHost.Details inferenceHost) { }

    public record RuntimeInfo(String generationModel, String embeddingModel, String embeddingDimensions,
                              int topK, double minScore, String javaVersion, String springBootVersion,
                              String springAiVersion, String database, String deployment, List<LoadedModel> loadedModels, String configuredModel, String modelStatus, InferenceHost.Details inferenceHost, ProviderInfo preferredProvider, ProviderInfo lastUsedProvider, String generationState) { }

    public record LoadedModel(String name, String parameterSize, long sizeBytes, long vramBytes) { }
}
