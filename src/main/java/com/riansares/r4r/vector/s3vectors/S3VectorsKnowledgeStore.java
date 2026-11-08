package com.riansares.r4r.vector.s3vectors;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.config.RagVectorProperties;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.DistanceMetric;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/** Read-only secondary over a manually imported corpus; no PostgreSQL dependency. */
public final class S3VectorsKnowledgeStore implements KnowledgeVectorStore {
    private final S3VectorsClient client;
    private final EmbeddingModel embeddings;
    private final RagVectorProperties.S3Vectors config;
    private final S3VectorMetadataMapper metadata;
    private final S3VectorScoreMapper scores;

    public S3VectorsKnowledgeStore(S3VectorsClient client, EmbeddingModel embeddings,
            RagVectorProperties.S3Vectors config, S3VectorMetadataMapper metadata, S3VectorScoreMapper scores) {
        this.client = Objects.requireNonNull(client, "client");
        this.embeddings = Objects.requireNonNull(embeddings, "embeddings");
        this.config = Objects.requireNonNull(config, "config");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.scores = Objects.requireNonNull(scores, "scores");
        config.validate();
    }

    @Override
    public void index(List<MarkdownChunk> chunks) {
        throw new UnsupportedOperationException("S3 Vectors is read-only; import existing vectors manually");
    }

    @Override
    public void replaceSource(String source, List<MarkdownChunk> chunks) {
        throw new UnsupportedOperationException("S3 Vectors is read-only; PostgreSQL owns source replacement");
    }

    @Override
    public List<MarkdownChunk> search(String query, int topK, double minScore) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        if (topK <= 0 || topK > 10_000) throw new IllegalArgumentException("S3 Vectors topK must be between 1 and 10000");
        if (!Double.isFinite(minScore) || minScore < 0 || minScore > 1) {
            throw new IllegalArgumentException("minScore must be finite and between 0.0 and 1.0");
        }
        checkCancelled();
        float[] vector = embeddings.embed(query); // Once per search, reused for every page.
        if (vector == null || vector.length != 768) throw invalid("query embedding must have 768 dimensions");
        List<Float> values = new ArrayList<>(vector.length);
        double squaredNorm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw invalid("query embedding has non-finite values");
            squaredNorm += (double) value * value;
            values.add(value);
        }
        if (squaredNorm == 0) throw invalid("query embedding must not be zero for cosine distance");
        QueryVectorsRequest base = QueryVectorsRequest.builder()
                .vectorBucketName(config.bucket()).indexName(config.index()).topK(topK)
                .queryVector(VectorData.fromFloat32(values)).returnDistance(true).returnMetadata(true).build();
        List<Hit> hits = new ArrayList<>();
        var tokens = new HashSet<String>();
        var keys = new HashSet<String>();
        String token = null;
        int pages = 0;
        do {
            checkCancelled();
            if (++pages > topK + 1) throw invalid("pagination exceeded a bounded number of pages");
            var page = client.queryVectors(base.toBuilder().nextToken(token).build());
            if (page == null || page.distanceMetric() != DistanceMetric.COSINE) {
                throw invalid("query response must specify cosine distance");
            }
            for (var output : page.vectors()) {
                if (output.key() == null || output.key().isBlank() || !keys.add(output.key())) {
                    throw invalid("query returned missing or duplicate vector keys");
                }
                if (output.distance() == null) throw invalid("query returned no distance");
                double distance = output.distance().doubleValue();
                scores.toScore(distance); // Validate even candidates excluded by the threshold.
                hits.add(new Hit(distance, metadata.fromDocument(output.metadata())));
                if (hits.size() > topK) throw invalid("query returned more than the requested topK");
            }
            token = page.nextToken();
            if (token != null && token.isBlank()) token = null;
            if (token != null && !tokens.add(token)) throw invalid("query repeated a pagination token");
        } while (token != null);
        checkCancelled();
        return hits.stream().sorted(Comparator.comparingDouble(Hit::distance))
                .filter(hit -> scores.meetsThreshold(hit.distance(), minScore)).limit(topK)
                .map(Hit::chunk).toList(); // Stable sort retains received order for equal distances.
    }

    private record Hit(double distance, MarkdownChunk chunk) { }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid S3 vector search: " + message);
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Vector search cancelled");
    }
}
