package com.riansares.r4r.rag.api;

import com.riansares.r4r.config.RagRetrievalProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagRuntimeController {

    private final RagRetrievalProperties retrieval;
    private final String generationModel;
    private final String embeddingModel;
    private final String embeddingDimensions;
    private final String databaseName;

    public RagRuntimeController(
            RagRetrievalProperties retrieval,
            @Value("${spring.ai.ollama.chat.options.model:No disponible}") String generationModel,
            @Value("${spring.ai.ollama.embedding.options.model:No disponible}") String embeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:No disponible}") String embeddingDimensions,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.retrieval = retrieval;
        this.generationModel = generationModel;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        this.databaseName = databaseName(datasourceUrl);
    }

    @GetMapping("/runtime")
    public RuntimeInfo runtime() {
        return new RuntimeInfo(generationModel, embeddingModel, embeddingDimensions,
                retrieval.topK(), retrieval.minScore(), System.getProperty("java.version"),
                SpringBootVersion.getVersion(), versionOf(ChatModel.class), databaseName,
                "Docker Compose");
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
                              String springAiVersion, String database, String deployment) { }
}
