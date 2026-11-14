package com.riansares.r4r.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.config.*;
import com.riansares.r4r.evaluation.EvaluationQuestions;
import com.riansares.r4r.rag.*;
import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import org.springframework.ai.embedding.*;
import org.springframework.ai.ollama.api.*;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Explicit read-only diagnostic. One query embedding per case, no generation, indexing or AWS. */
public final class RetrievalThresholdDiagnostic {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("--run")) throw new IllegalArgumentException("Requires --run");
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("diagnostic", Map.of(
                "spring.config.location", "file:./src/main/resources/application.yml")));
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        PublicRagEnvironment.apply(env);
        var failover = Binder.get(env).bind("r4r.rag.embedding", EmbeddingFailoverProperties.class).get();
        var options = OllamaOptions.builder().model(env.getRequiredProperty("spring.ai.ollama.embedding.options.model")).build();
        var embedding = new ObservedEmbeddingModel(OllamaApi.builder().baseUrl(failover.providers().getFirst().baseUrl()).build(),
                options, new EmbeddingProviderRouter(failover, options, Duration.ofSeconds(180)));
        var config = Binder.get(env).bind("r4r.rag.retrieval", RagRetrievalProperties.class).get();
        var rerank = Binder.get(env).bind("r4r.rag.retrieval.rerank", NomicRerankProperties.class).get();
        var credentials = new Properties();
        credentials.setProperty("user", env.getRequiredProperty("spring.datasource.username"));
        credentials.setProperty("password", env.getRequiredProperty("spring.datasource.password"));
        credentials.setProperty("options", "-c default_transaction_read_only=on");
        credentials.setProperty("connectTimeout", "5");
        credentials.setProperty("socketTimeout", "60");
        var json = new ObjectMapper();
        var output = Path.of("target/evaluation/retrieval-thresholds.json");
        Files.createDirectories(output.getParent());
        var results = new ArrayList<Map<String,Object>>();
        if (Files.exists(output)) results.addAll(json.convertValue(json.readTree(output.toFile()).path("results"),
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>() { }));
        try (var connection = DriverManager.getConnection(env.getRequiredProperty("spring.datasource.url"), credentials)) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            if (!"on".equals(jdbc.queryForObject("SELECT current_setting('transaction_read_only')", String.class)))
                throw new IllegalStateException("Read-only connection required");
            long count = jdbc.queryForObject("SELECT count(*) FROM vector_store", Long.class);
            if (count != 1375) throw new IllegalStateException("Expected the fixed 1375-vector corpus");
            for (String language : List.of("es", "en")) for (var question : EvaluationQuestions.cases(language)) {
                if (results.stream().filter(row -> language.equals(row.get("language"))
                        && question.caseId().equals(row.get("caseId"))).count() == 3) continue;
                long start = System.nanoTime();
                float[] vector = embedding.embed(question.question());
                double embedMs = (System.nanoTime() - start) / 1e6;
                EmbeddingModel fixed = new EmbeddingModel() {
                    public float[] embed(String ignored) { return vector; }
                    public float[] embed(org.springframework.ai.document.Document ignored) { throw new UnsupportedOperationException(); }
                    public EmbeddingResponse call(EmbeddingRequest ignored) { return new EmbeddingResponse(List.of(new Embedding(vector, 0))); }
                    public int dimensions() { return 768; }
                };
                var nativeStore = PgVectorStore.builder(jdbc, fixed).dimensions(768).initializeSchema(false)
                        .vectorTableValidationsEnabled(true).build();
                var store = new PgVectorKnowledgeStore(nativeStore, jdbc, rerank);
                for (double threshold : List.of(.63, .65, .70)) {
                    long searchStart = System.nanoTime();
                    var selected = store.search(question.question(), config.topK(), threshold);
                    results.add(Map.of("language", language, "caseId", question.caseId(), "number", question.number(),
                            "threshold", threshold, "embeddingMs", embedMs, "retrievalMs", (System.nanoTime()-searchStart)/1e6,
                            "chunks", selected));
                }
                json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), Map.of(
                        "vectorCount", count, "topK", config.topK(), "rerank", rerank, "results", results));
                System.out.printf("RETRIEVAL_DIAGNOSTIC %s/%s (%d)%n", language, question.caseId(), question.number());
            }
        }
    }
}
