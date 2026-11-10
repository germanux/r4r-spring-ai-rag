package com.riansares.r4r.titan;

import com.amazonaws.services.lambda.runtime.*;
import com.fasterxml.jackson.databind.*;
import com.riansares.r4r.vector.s3vectors.S3VectorMetadataMapper;
import com.riansares.r4r.vector.s3vectors.S3VectorScoreMapper;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
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
                .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()).build();
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
        List<Float> data = new TitanEmbeddingClient(bedrock).embed(question);
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
                double score = new S3VectorScoreMapper().toScore(Math.max(0, Math.min(2, distance)));
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
                "provider", Map.of("embedding", MODEL, "vectorStore", "aws-s3-vectors", "index", INDEX, "dimensions", 1024),
                "calibration", minimumSimilarity == null ? "uncalibrated" : "configured",
                "abstained", documents.isEmpty());
    }
    static List<Float> decodeEmbedding(JsonNode body) {
        return TitanEmbeddingClient.decode(body);
    }
    static Map<String, Object> document(String id, double score, Document metadata) {
        var mapper = new S3VectorMetadataMapper();
        var values = mapper.metadataValues(metadata);
        var chunk = mapper.fromMetadata(values);
        return Map.of("id", id, "text", chunk.content(), "score", score, "source", chunk.source(),
                "ordinal", chunk.index(), "headingPath", chunk.headingPath(), "metadata", values);
    }
    static double elapsed(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
}
