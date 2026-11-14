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
        boolean focused = args.length == 2 && args[1].equals("--cases-07-10");
        if (args.length < 1 || !args[0].equals("--run") || (args.length != 1 && !focused))
            throw new IllegalArgumentException("Requires --run [--cases-07-10]");
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
        var output = Path.of(focused ? "target/evaluation/retrieval-cases-07-10.json" : "target/evaluation/retrieval-thresholds.json");
        Files.createDirectories(output.getParent());
        var results = new ArrayList<Map<String,Object>>();
        if (!focused && Files.exists(output)) results.addAll(json.convertValue(json.readTree(output.toFile()).path("results"),
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>() { }));
        try (var connection = DriverManager.getConnection(env.getRequiredProperty("spring.datasource.url"), credentials)) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            if (!"on".equals(jdbc.queryForObject("SELECT current_setting('transaction_read_only')", String.class)))
                throw new IllegalStateException("Read-only connection required");
            long count = jdbc.queryForObject("SELECT count(*) FROM vector_store", Long.class);
            if (count != 1375) throw new IllegalStateException("Expected the fixed 1375-vector corpus");
            for (String language : List.of("es", "en")) for (var question : EvaluationQuestions.cases(language)) {
                if (focused && !List.of("case-07", "case-10").contains(question.caseId())) continue;
                if (results.stream().filter(row -> language.equals(row.get("language"))
                        && question.caseId().equals(row.get("caseId"))).count() == 3) continue;
                long start = System.nanoTime();
                float[] vector = embedding.embed(focused ? ObservedEmbeddingModel.retrievalQuery(question.question()) : question.question());
                double embedMs = (System.nanoTime() - start) / 1e6;
                EmbeddingModel fixed = new EmbeddingModel() {
                    public float[] embed(String ignored) { return vector; }
                    public float[] embed(org.springframework.ai.document.Document ignored) { throw new UnsupportedOperationException(); }
                    public EmbeddingResponse call(EmbeddingRequest ignored) { return new EmbeddingResponse(List.of(new Embedding(vector, 0))); }
                    public int dimensions() { return 1024; }
                };
                var nativeStore = PgVectorStore.builder(jdbc, fixed).dimensions(1024).initializeSchema(false)
                        .vectorTableValidationsEnabled(true).build();
                var store = new PgVectorKnowledgeStore(nativeStore, jdbc, rerank);
                if (focused) {
                    var raw = nativeStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query(ObservedEmbeddingModel.retrievalQuery(question.question()))
                            .topK(10).similarityThresholdAll().build());
                    var qualified = nativeStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query(ObservedEmbeddingModel.retrievalQuery(question.question()))
                            .topK(new NomicCandidateReranker(rerank).candidateCount(config.topK()))
                            .similarityThreshold(.63).build());
                    var selected = store.search(question.question(), config.topK(), .63);
                    var candidates = new ArrayList<Map<String, Object>>();
                    for (var document : raw) {
                        var metadata = document.getMetadata();
                        candidates.add(Map.of("rank", candidates.size() + 1, "id", document.getId(),
                                "cosine", document.getScore(), "source", metadata.get("source"),
                                "heading", metadata.get("headingPath"), "ordinal", metadata.get("ordinal"),
                                "passesThreshold", qualified.stream().anyMatch(d -> d.getId().equals(document.getId())),
                                "keptByReranker", selected.stream().anyMatch(c -> c.source().equals(metadata.get("source"))
                                        && c.headingPath().equals(metadata.get("headingPath"))
                                        && c.index() == ((Number) metadata.get("ordinal")).intValue())));
                    }
                    var row = new LinkedHashMap<String, Object>();
                    row.put("language", language); row.put("caseId", question.caseId());
                    row.put("question", question.question()); row.put("threshold", .63);
                    row.put("embeddingMs", embedMs); row.put("candidates", candidates);
                    row.put("qualifiedCount", qualified.size()); row.put("selected", selected);
                    results.add(row);
                    System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(row));
                }
                for (double threshold : focused ? List.<Double>of() : List.of(.63, .65, .70)) {
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
