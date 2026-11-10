package com.riansares.r4r.vector.titan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.rag.RagProgress;
import com.riansares.r4r.vector.VectorStoreUnavailableException;
import com.riansares.r4r.vector.config.TitanRetrievalProperties;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import com.riansares.r4r.vector.s3vectors.S3VectorMetadataMapper;
import java.util.*;
import java.util.concurrent.CancellationException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;

/** Read-only bridge to the private Titan Lambda. Never generates or forwards a Nomic vector. */
public final class TitanLambdaKnowledgeStore implements KnowledgeVectorStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LambdaClient client;
    private final TitanRetrievalProperties config;
    private final S3VectorMetadataMapper metadata = new S3VectorMetadataMapper();
    public TitanLambdaKnowledgeStore(LambdaClient client, TitanRetrievalProperties config) {
        this.client = Objects.requireNonNull(client); this.config = Objects.requireNonNull(config); config.validate();
    }
    @Override public void index(List<MarkdownChunk> chunks) { throw new UnsupportedOperationException("Titan retrieval is read-only"); }
    @Override public void replaceSource(String source, List<MarkdownChunk> chunks) { throw new UnsupportedOperationException("Titan retrieval is read-only"); }
    @Override public List<MarkdownChunk> search(String query, int topK, double minScore) {
        if (query == null || query.isBlank() || query.length() > 10000 || topK < 1
                || !Double.isFinite(minScore) || minScore < 0 || minScore > 1) throw new IllegalArgumentException("Invalid retrieval request");
        checkCancelled();
        int limit = Math.min(topK, config.maxTopK());
        long start = System.nanoTime();
        try {
            InvokeResponse response;
            try {
                response = client.invoke(InvokeRequest.builder().functionName(config.functionName())
                        .invocationType(InvocationType.REQUEST_RESPONSE).payload(SdkBytes.fromByteArray(
                                JSON.writeValueAsBytes(Map.of("question", query, "topK", limit)))).build());
            } finally { RagProgress.observeLambdaInvocation((System.nanoTime() - start) / 1_000_000.0); }
            double invokeMs = (System.nanoTime() - start) / 1_000_000.0;
            checkCancelled();
            if (response == null || !Integer.valueOf(200).equals(response.statusCode()) || response.functionError() != null
                    || response.payload() == null || response.payload().asByteArray().length > 1024 * 1024)
                throw new IllegalStateException("Invalid Lambda invocation response");
            var body = JSON.readTree(response.payload().asByteArray());
            if (!"amazon.titan-embed-text-v2:0".equals(body.at("/provider/embedding").asText())
                    || !"r4r-titan-v2-1024".equals(body.at("/provider/index").asText())
                    || body.at("/provider/dimensions").asInt() != 1024
                    || !"aws-s3-vectors".equals(body.at("/provider/vectorStore").asText()))
                throw new IllegalStateException("Unexpected embedding space");
            var docs = body.path("documents");
            if (!docs.isArray() || docs.size() > limit || !body.path("abstained").isBoolean()
                    || body.path("abstained").booleanValue() != docs.isEmpty()) throw new IllegalStateException("Invalid retrieval documents");
            var hits = new ArrayList<Hit>(); var ids = new HashSet<String>();
            for (var doc : docs) {
                if (!doc.path("id").isTextual() || doc.path("id").asText().isBlank() || !ids.add(doc.path("id").asText())
                        || !doc.path("score").isNumber() || !doc.path("metadata").isObject()) throw new IllegalStateException("Invalid retrieval hit");
                double score = doc.path("score").doubleValue();
                if (!Double.isFinite(score) || score < -1 || score > 1) throw new IllegalStateException("Invalid Titan score");
                Map<String, Object> values = JSON.convertValue(doc.path("metadata"), new TypeReference<>() {});
                var chunk = metadata.fromMetadata(values);
                if (!doc.path("text").isTextual() || !doc.path("text").asText().equals(chunk.content())
                        || !doc.path("source").asText().equals(chunk.source()) || !doc.path("ordinal").isIntegralNumber()
                        || !doc.path("ordinal").canConvertToInt() || doc.path("ordinal").intValue() != chunk.index()
                        || !JSON.valueToTree(chunk.headingPath()).equals(doc.path("headingPath")))
                    throw new IllegalStateException("Inconsistent retrieval provenance");
                hits.add(new Hit(score, chunk));
            }
            double embeddingMs = metric(body, "embeddingMs"), searchMs = metric(body, "vectorSearchMs");
            double totalMs = metric(body, "totalMs");
            if (totalMs < Math.max(embeddingMs, searchMs)) throw new IllegalStateException("Invalid Lambda timing");
            RagProgress.observeLambdaRetrieval(embeddingMs, searchMs, invokeMs);
            // Separate calibration, configured explicitly for Titan; the port's Nomic minScore is not transferable.
            return hits.stream().sorted(Comparator.comparingDouble(Hit::score).reversed())
                    .filter(hit -> hit.score() >= config.minimumSimilarity()).map(Hit::chunk).toList();
        } catch (CancellationException cancelled) { throw cancelled; }
        catch (Exception failure) {
            var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
            for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
                if (cause instanceof CancellationException) throw new CancellationException("Titan retrieval cancelled");
                if (cause instanceof InterruptedException || cause instanceof java.nio.channels.ClosedByInterruptException
                        || cause instanceof java.io.InterruptedIOException && !(cause instanceof java.net.SocketTimeoutException)) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Titan retrieval cancelled");
                }
            }
            checkCancelled();
            // SDK/function messages and response bodies can contain private input. Public contract stays controlled.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Titan retrieval result=failed errorType={}", failure.getClass().getSimpleName());
            throw new VectorStoreUnavailableException("Titan retrieval unavailable", null);
        }
    }
    private static double metric(JsonNode body, String name) {
        var node = body.path("metrics").path(name);
        if (!node.isNumber() || !Double.isFinite(node.doubleValue()) || node.doubleValue() < 0)
            throw new IllegalStateException("Invalid Lambda metrics");
        return node.doubleValue();
    }
    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Titan retrieval cancelled");
    }
    private record Hit(double score, MarkdownChunk chunk) { }
}
