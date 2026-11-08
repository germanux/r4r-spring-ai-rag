package com.riansares.r4r.rag;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;

/** Retains Ollama's bean type/autoconfiguration boundary and calls Spring AI exactly once. */
public final class ObservedEmbeddingModel extends OllamaEmbeddingModel {
    private final String model;
    private final EmbeddingProviderRouter router;
    public ObservedEmbeddingModel(OllamaApi api, OllamaOptions options) {
        this(api, options, null);
    }
    public ObservedEmbeddingModel(OllamaApi api, OllamaOptions options, EmbeddingProviderRouter router) {
        super(api, options, ObservationRegistry.NOOP, ModelManagementOptions.defaults());
        this.model = options.getModel();
        this.router = router;
    }
    @Override public EmbeddingResponse call(EmbeddingRequest request) {
        return RagProgress.observeEmbedding(model, () -> router == null ? super.call(request) : router.call(request));
    }
    @Override public int dimensions() { return router == null ? super.dimensions() : 768; }
}
