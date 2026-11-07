package com.riansares.r4r.rag.api;

import com.riansares.r4r.config.RagRetrievalProperties;
import com.riansares.r4r.rag.OllamaModelPreloader;
import com.riansares.r4r.rag.GenerationRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import com.riansares.r4r.rag.OllamaModelPreloader.LoadedModel;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagRuntimeController {

    private final RagRetrievalProperties retrieval;
    private final String embeddingModel;
    private final String embeddingDimensions;
    private final String databaseName;
    private final OllamaModelPreloader preloader;
    private final GenerationRouter router;
    @Value("${r4r.rag.openai.enabled:false}") private boolean remoteEnabled;
    @Value("${r4r.rag.openai.model:gpt-6-luna}") private String remoteModel = "gpt-6-luna";
    public record RemoteFallback(boolean enabled, String model) { }


    public RagRuntimeController(
            RagRetrievalProperties retrieval,
            @Value("${spring.ai.ollama.embedding.options.model:No disponible}") String embeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:No disponible}") String embeddingDimensions,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            OllamaModelPreloader preloader, GenerationRouter router) {
        this.retrieval = retrieval;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        this.databaseName = databaseName(datasourceUrl);
        this.preloader = preloader;
        this.router = router;
    }

    @PostMapping("/warmup")
    public org.springframework.http.ResponseEntity<OllamaModelPreloader.WarmupResult> warmup() {
        var result = preloader.requestWarmup();
        return org.springframework.http.ResponseEntity.status(result.accepted().isEmpty() ? 200 : 202)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header("Retry-After", Long.toString(Math.max(1, result.retryAfterSeconds()))).body(result);
    }

    @GetMapping("/runtime")
    public RuntimeInfo runtime() {
        GenerationRouter.Execution execution = router.execution();
        GenerationRouter.Provider last = execution.provider();
        GenerationRouter.Provider selected = last == null ? router.preferred() : last;
        boolean remote = "OpenAI".equals(selected.name());
        var states = preloader.modelStates();
        var memory = execution.failed() || remote ? new OllamaModelPreloader.MemoryState(false, List.of()) : preloader.memoryFor(selected);
        var localState = states.stream().filter(m -> m.model().equals(selected.model())).findFirst().orElse(null);
        boolean loaded = memory.models().stream().anyMatch(m -> canonical(selected.model()).equals(canonical(m.name())));
        String status = remote ? "No aplica (OpenAI)" : localState == null
                ? !memory.available() ? "Ollama no disponible" : loaded ? "Modelo cargado" : "Modelo descargado de memoria"
                : switch (localState.state()) {
                    case "loading" -> "Cargando modelo";
                    case "loaded" -> "Modelo cargado";
                    case "unloaded" -> "Modelo descargado de memoria";
                    case "available" -> "Modelo disponible";
                    case "unknown" -> "Pendiente de comprobar";
                    default -> "Ollama no disponible";
                };
        return new RuntimeInfo(execution.failed() ? null : selected.model(), embeddingModel, embeddingDimensions,
                retrieval.topK(), retrieval.minScore(), System.getProperty("java.version"),
                SpringBootVersion.getVersion(), versionOf(ChatModel.class), databaseName,
                "Docker Compose", memory.models(), router.preferred().model(), status,
                execution.failed() ? null : remote ? new InferenceHost.Details("cloud", null, null, null, null) : InferenceHost.identify(selected.baseUrl(), selected.model()), providerInfo(router.preferred()),
                last == null ? null : providerInfo(last), execution.failed() ? "failed" : last == null ? "idle" : "success", new RemoteFallback(remoteEnabled, remoteModel), states);
    }

    private static String canonical(String name) { return name.contains(":") ? name : name + ":latest"; }
    private static String versionOf(Class<?> type) {
        String version = type.getPackage().getImplementationVersion();
        return version == null ? "No disponible" : version;
    }

    private static String databaseName(String url) {
        int slash = url.lastIndexOf('/');
        return slash >= 0 && slash + 1 < url.length() ? url.substring(slash + 1) : "PostgreSQL · pgvector";
    }

    private ProviderInfo providerInfo(GenerationRouter.Provider provider) {
        return new ProviderInfo(provider.name(), provider.model(), "OpenAI".equals(provider.name()) ? new InferenceHost.Details("cloud", null, null, null, null) : InferenceHost.identify(provider.baseUrl(), provider.model()));
    }
    public record ProviderInfo(String name, String model, InferenceHost.Details inferenceHost) { }

    public record RuntimeInfo(String generationModel, String embeddingModel, String embeddingDimensions,
                              int topK, double minScore, String javaVersion, String springBootVersion,
                              String springAiVersion, String database, String deployment, List<LoadedModel> loadedModels, String configuredModel, String modelStatus, InferenceHost.Details inferenceHost, ProviderInfo preferredProvider, ProviderInfo lastUsedProvider, String generationState, RemoteFallback remoteFallback, List<OllamaModelPreloader.ModelStatus> models) { }

}
