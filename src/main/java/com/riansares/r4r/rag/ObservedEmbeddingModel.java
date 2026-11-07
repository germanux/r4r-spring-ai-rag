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
    public ObservedEmbeddingModel(OllamaApi api, OllamaOptions options) {
        super(api, options, ObservationRegistry.NOOP, ModelManagementOptions.defaults());
        this.model = options.getModel();
    }
    @Override public EmbeddingResponse call(EmbeddingRequest request) {
        return RagProgress.observeEmbedding(model, () -> super.call(request));
    }
}
