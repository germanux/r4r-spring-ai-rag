package com.riansares.r4r.vector.s3vectors;

import com.riansares.r4r.config.NomicRerankProperties;
import com.riansares.r4r.vector.NomicCandidateReranker;
import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.config.RagVectorProperties;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.DistanceMetric;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Read-only secondary over a manually imported corpus; no PostgreSQL dependency. */
public final class S3VectorsKnowledgeStore implements KnowledgeVectorStore {
    private static final Logger log = LoggerFactory.getLogger(S3VectorsKnowledgeStore.class);
    private static final Set<String> AWS_ERROR_CODES = Set.of("AccessDenied", "AccessDeniedException",
            "ExpiredToken", "ExpiredTokenException", "InvalidClientTokenId", "UnrecognizedClientException",
            "InvalidSignatureException", "SignatureDoesNotMatch", "RequestExpired", "ValidationException",
            "NotFoundException", "ResourceNotFoundException", "ServiceUnavailableException",
            "InternalServerException", "ThrottlingException", "RequestTimeout", "RequestTimeoutException",
            "TooManyRequestsException");
    private final java.util.function.Function<QueryVectorsRequest, software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse> transport;
    private final EmbeddingModel embeddings;
    private final RagVectorProperties.S3Vectors config;
    private final S3VectorMetadataMapper metadata;
    private final S3VectorScoreMapper scores;
    private final NomicCandidateReranker reranker;

    public S3VectorsKnowledgeStore(S3VectorsClient client, EmbeddingModel embeddings,
            RagVectorProperties.S3Vectors config, S3VectorMetadataMapper metadata, S3VectorScoreMapper scores) {
        this(client, embeddings, config, metadata, scores, NomicRerankProperties.defaults());
    }

    public S3VectorsKnowledgeStore(S3VectorsClient client, EmbeddingModel embeddings,
            RagVectorProperties.S3Vectors config, S3VectorMetadataMapper metadata, S3VectorScoreMapper scores,
            NomicRerankProperties ranking) {
        this(client::queryVectors, embeddings, config, metadata, scores, ranking);
    }

    public S3VectorsKnowledgeStore(java.util.function.Function<QueryVectorsRequest, software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse> transport, EmbeddingModel embeddings,
            RagVectorProperties.S3Vectors config, S3VectorMetadataMapper metadata, S3VectorScoreMapper scores, NomicRerankProperties ranking) {
        this.reranker = new NomicCandidateReranker(ranking);
        this.transport = Objects.requireNonNull(transport);
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
        int candidateCount = reranker.candidateCount(topK);
        checkCancelled();
        String stage = "embedding";
        try {
            float[] vector = embeddings.embed(com.riansares.r4r.rag.ObservedEmbeddingModel.retrievalQuery(query)); // Once, reused for every page.
            stage = "embedding_validation";
            if (vector == null || vector.length != 1024) throw invalid("query embedding must have 1024 dimensions");
            List<Float> values = new ArrayList<>(vector.length);
            double squaredNorm = 0;
            for (float value : vector) {
                if (!Float.isFinite(value)) throw invalid("query embedding has non-finite values");
                squaredNorm += (double) value * value;
                values.add(value);
            }
            if (squaredNorm == 0) throw invalid("query embedding must not be zero for cosine distance");
            QueryVectorsRequest base = QueryVectorsRequest.builder()
                    .vectorBucketName(config.bucket()).indexName(config.index()).topK(candidateCount)
                    .queryVector(VectorData.fromFloat32(values)).returnDistance(true).returnMetadata(true).build();
            List<Hit> hits = new ArrayList<>();
            var tokens = new HashSet<String>();
            var keys = new HashSet<String>();
            String token = null;
            int pages = 0;
            do {
                checkCancelled();
                if (++pages > candidateCount + 1) throw invalid("pagination exceeded a bounded number of pages");
                stage = "aws_query";
                var page = transport.apply(base.toBuilder().nextToken(token).build());
                stage = "response_validation";
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
                    if (hits.size() > candidateCount) throw invalid("query returned more than the requested topK");
                }
                token = page.nextToken();
                if (token != null && token.isBlank()) token = null;
                if (token != null && !tokens.add(token)) throw invalid("query repeated a pagination token");
            } while (token != null);
            checkCancelled();
            var eligible = hits.stream().sorted(Comparator.comparingDouble(Hit::distance))
                    .filter(hit -> scores.meetsThreshold(hit.distance(), minScore))
                    .map(hit -> new NomicCandidateReranker.Candidate(hit.chunk(), scores.toScore(hit.distance())))
                    .toList();
            return reranker.select(query, eligible, topK); // Stable sort retains received order for equal distances.
        } catch (RuntimeException failure) {
            logFailure(stage, failure);
            throw failure; // Preserve the original error and the router's cancellation/503 contract.
        }
    }

    /** Structured diagnostics only; SDK messages can contain credentials, paths and response bodies. */
    private void logFailure(String stage, RuntimeException failure) {
        if (Thread.currentThread().isInterrupted()) return;
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        Throwable root = failure;
        AwsServiceException service = null;
        String reason = stage.equals("response_validation") ? "invalid_response"
                : stage.equals("embedding_validation") ? "invalid_embedding" : "unexpected";
        for (Throwable cause = failure; cause != null && seen.size() < 32 && seen.add(cause); cause = cause.getCause()) {
            root = cause;
            if (cause instanceof CancellationException || cause instanceof InterruptedException
                    || cause instanceof java.nio.channels.ClosedByInterruptException
                    || cause instanceof java.io.InterruptedIOException && !(cause instanceof java.net.SocketTimeoutException)) return;
            if (cause instanceof AwsServiceException aws) service = aws;
            if (cause instanceof SdkClientException sdk) {
                if ("unexpected".equals(reason)) reason = "sdk_client";
                // This fixed prefix comes from AwsCredentialsProviderChain; never log the provider details that follow it.
                if (sdk.getMessage() != null && sdk.getMessage().startsWith("Unable to load credentials from any of the providers in the chain"))
                    reason = "credentials_unavailable";
            }
            if (cause instanceof ApiCallTimeoutException || cause instanceof ApiCallAttemptTimeoutException
                    || cause instanceof java.net.SocketTimeoutException) reason = "timeout";
            else if (cause instanceof java.net.UnknownHostException) reason = "dns";
            else if (cause instanceof java.net.ConnectException) reason = "connect";
            else if (cause instanceof javax.net.ssl.SSLException) reason = "tls";
        }
        int status = 0;
        String code = "none";
        if (service != null) {
            reason = "aws_service";
            status = service.statusCode();
            if (service.awsErrorDetails() != null && service.awsErrorDetails().errorCode() != null) {
                String supplied = service.awsErrorDetails().errorCode();
                code = AWS_ERROR_CODES.contains(supplied) ? supplied : "other";
            }
        }
        log.warn("S3 Vectors result=failed stage={} reason={} errorType={} rootCauseType={} httpStatus={} awsErrorCode={} region={} bucket={} index={}",
                stage, reason, failure.getClass().getSimpleName(), root.getClass().getSimpleName(), status, code,
                config.region(), config.bucket(), config.index());
    }

    private record Hit(double distance, MarkdownChunk chunk) { }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid S3 vector search: " + message);
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Vector search cancelled");
    }
}
