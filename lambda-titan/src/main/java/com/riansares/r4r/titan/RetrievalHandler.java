package com.riansares.r4r.titan;

import com.amazonaws.services.lambda.runtime.*;
import com.fasterxml.jackson.databind.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import java.io.*;
import java.time.Duration;
import java.util.*;

/** Private synchronous retrieval only. No generation and no Nomic embedding dependencies. */
public final class RetrievalHandler implements RequestStreamHandler {
    static final ObjectMapper JSON = new ObjectMapper();
    public static final String MODEL = "amazon.titan-embed-text-v2:0";
    static final String BUCKET = "amzn-r4r-vectors", INDEX = "r4r-titan-v2-1024";
    private final BedrockRuntimeClient bedrock;
    private final S3VectorsClient vectors;
    private final Double minimumSimilarity;

    // Lazy holder reuses both clients across warm invocations; Lambda uses its execution role.
    private static final class Clients {
        static final ClientOverrideConfiguration CONFIG = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(10)).apiCallAttemptTimeout(Duration.ofSeconds(8))
                .retryStrategy(StandardRetryStrategy.builder().maxAttempts(2).build()).build();
        static final BedrockRuntimeClient BEDROCK = BedrockRuntimeClient.builder().region(Region.EU_NORTH_1)
                .httpClientBuilder(http()).overrideConfiguration(CONFIG).build();
        static final S3VectorsClient VECTORS = S3VectorsClient.builder().region(Region.EU_NORTH_1)
                .httpClientBuilder(http()).overrideConfiguration(CONFIG).build();
        static UrlConnectionHttpClient.Builder http() {
            return UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(2)).socketTimeout(Duration.ofSeconds(8));
        }
    }
    public RetrievalHandler() { this(Clients.BEDROCK, Clients.VECTORS, threshold(System.getenv("TITAN_MIN_SIMILARITY"))); }
    RetrievalHandler(BedrockRuntimeClient bedrock, S3VectorsClient vectors, Double minimumSimilarity) {
        this.bedrock = Objects.requireNonNull(bedrock); this.vectors = Objects.requireNonNull(vectors);
        if (minimumSimilarity != null && (!Double.isFinite(minimumSimilarity) || minimumSimilarity < -1 || minimumSimilarity > 1))
            throw new IllegalArgumentException("Invalid Titan similarity threshold");
        this.minimumSimilarity = minimumSimilarity;
    }
    static Double threshold(String value) { return value == null || value.isBlank() ? null : Double.valueOf(value); }
    @Override public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        long start = System.nanoTime();
        try {
            byte[] payload = input.readNBytes(65537);
            if (payload.length > 65536) throw new IllegalArgumentException("Request too large");
            var result = retrieve(JSON.readTree(payload));
            JSON.writeValue(output, result);
            context.getLogger().log(JSON.writeValueAsString(Map.of("event", "retrieval", "result", "success",
                    "metrics", result.get("metrics"), "requestId", context.getAwsRequestId())) + "\n");
        } catch (Exception failure) {
            // Never log SDK messages, stack traces, input, or corpus content.
            context.getLogger().log(JSON.writeValueAsString(Map.of("event", "retrieval", "result", "failed",
                    "errorType", failure.getClass().getSimpleName(), "totalMs", elapsed(start),
                    "requestId", context.getAwsRequestId())) + "\n");
            throw new IOException(failure instanceof IllegalArgumentException ? "Invalid retrieval request" : "Retrieval unavailable");
        }
    }
    Map<String, Object> retrieve(JsonNode request) throws IOException {
        long start = System.nanoTime();
        if (request == null || !request.isObject() || !request.path("question").isTextual())
            throw new IllegalArgumentException("question required");
        String question = request.path("question").textValue();
        if (question.isBlank() || question.length() > 10000) throw new IllegalArgumentException("Invalid question length");
        JsonNode k = request.get("topK");
        if (k != null && (!k.isIntegralNumber() || !k.canConvertToInt())) throw new IllegalArgumentException("Invalid topK");
        int topK = k == null ? 5 : k.intValue();
        if (topK < 1 || topK > 20) throw new IllegalArgumentException("topK outside 1..20");
        var embedding = bedrock.invokeModel(InvokeModelRequest.builder().modelId(MODEL)
                .contentType("application/json").accept("application/json").body(SdkBytes.fromUtf8String(
                        JSON.writeValueAsString(Map.of("inputText", question, "dimensions", 1024, "normalize", true)))).build());
        List<Float> data = decodeEmbedding(JSON.readTree(embedding.body().asByteArray()));
        double embeddingMs = elapsed(start);
        long searchStart = System.nanoTime();
        var base = QueryVectorsRequest.builder().vectorBucketName(BUCKET).indexName(INDEX).topK(topK)
                .queryVector(VectorData.fromFloat32(data)).returnDistance(true).returnMetadata(true).build();
        List<Map<String, Object>> documents = new ArrayList<>();
        Set<String> tokens = new HashSet<>(), keys = new HashSet<>();
        String token = null;
        int count = 0;
        do {
            var page = vectors.queryVectors(base.toBuilder().nextToken(token).build());
            if (page.distanceMetric() != DistanceMetric.COSINE) throw new IllegalStateException("Invalid distance metric");
            for (var hit : page.vectors()) {
                if (++count > topK || hit.key() == null || !keys.add(hit.key()) || hit.distance() == null)
                    throw new IllegalStateException("Invalid vector response");
                double distance = hit.distance();
                if (!Double.isFinite(distance) || distance < -0.00001 || distance > 2.00001)
                    throw new IllegalStateException("Invalid distance");
                double score = Math.max(-1, Math.min(1, 1 - distance));
                var doc = document(hit.key(), score, hit.metadata());
                if (minimumSimilarity == null || score >= minimumSimilarity) documents.add(doc);
            }
            token = page.nextToken();
            if (token != null && token.isBlank()) token = null;
            if (token != null && (!tokens.add(token) || tokens.size() > topK)) throw new IllegalStateException("Invalid pagination");
        } while (token != null);
        documents.sort(Comparator.comparingDouble(d -> -((Number)d.get("score")).doubleValue()));
        return Map.of("documents", documents, "metrics", Map.of("embeddingMs", embeddingMs,
                "vectorSearchMs", elapsed(searchStart), "totalMs", elapsed(start)),
                "provider", Map.of("embedding", MODEL, "vectorStore", "aws-s3-vectors"),
                "calibration", minimumSimilarity == null ? "uncalibrated" : "configured",
                "abstained", documents.isEmpty());
    }
    static List<Float> decodeEmbedding(JsonNode body) {
        var values = body.path("embedding");
        if (!values.isArray() || values.size() != 1024) throw new IllegalStateException("Invalid embedding dimensions");
        List<Float> result = new ArrayList<>(1024); double norm = 0;
        for (var value : values) {
            if (!value.isNumber() || !Float.isFinite(value.floatValue())) throw new IllegalStateException("Invalid embedding value");
            float number = value.floatValue(); result.add(number); norm += (double)number * number;
        }
        if (norm == 0) throw new IllegalStateException("Zero embedding");
        return result;
    }
    static Map<String, Object> document(String id, double score, Document metadata) throws IOException {
        if (metadata == null || !metadata.isMap()) throw new IllegalStateException("Missing metadata");
        var node = JSON.readTree(metadata.toString());
        // SDK Document.toString is JSON; retain original metadata for provenance.
        if (!node.path("source_text").isTextual() || node.path("source_text").asText().isBlank()
                || !node.path("source").isTextual() || node.path("source").asText().isBlank()
                || !node.path("ordinal").isIntegralNumber() || !node.path("ordinal").canConvertToInt()
                || node.path("ordinal").intValue() < 0) throw new IllegalStateException("Invalid provenance");
        var headings = node.has("headingPath") ? node.get("headingPath") : JSON.createArrayNode();
        if (!headings.isArray()) throw new IllegalStateException("Invalid headings");
        for (var heading : headings) if (!heading.isTextual()) throw new IllegalStateException("Invalid heading");
        return Map.of("id", id, "text", node.get("source_text").textValue(), "score", score,
                "source", node.get("source").textValue(), "ordinal", node.get("ordinal").intValue(),
                "headingPath", JSON.convertValue(headings, List.class), "metadata", JSON.convertValue(node, Map.class));
    }
    static double elapsed(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
}
