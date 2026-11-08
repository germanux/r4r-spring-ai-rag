package com.riansares.r4r.rag;

import com.riansares.r4r.config.EmbeddingFailoverProperties;
import io.micrometer.observation.ObservationRegistry;
import java.io.EOFException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.channels.ClosedByInterruptException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Bounded, stateless failover: every batch starts again at the preferred laptop. */
public final class EmbeddingProviderRouter {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingProviderRouter.class);
    private static final Set<Integer> RECOVERABLE = Set.of(408, 429, 500, 502, 503, 504);
    private record Target(String name, String url, Duration timeout) { }
    private final List<Target> targets;
    private final EmbeddingFailoverProperties config;
    private final OllamaOptions options;
    private final Duration streamTimeout;
    private final String model;

    public EmbeddingProviderRouter(EmbeddingFailoverProperties config, OllamaOptions options, Duration streamTimeout) {
        this.config = config;
        this.options = OllamaOptions.fromOptions(options);
        this.model = canonical(options.getModel());
        this.streamTimeout = streamTimeout;
        if (!model.startsWith("nomic-embed-text:") || config.dimensions() != 768
                || config.expectedDigest() == null || !config.expectedDigest().matches("[a-fA-F0-9]{64}"))
            throw new EmbeddingConfigurationException("Compatible nomic model, SHA-256 digest and 768 dimensions required");
        targets = List.of(new Target("laptop", config.baseUrl(), config.readTimeout()),
                new Target("desktop", config.pcBaseUrl(), config.pcReadTimeout()),
                new Target("nas", config.nasBaseUrl(), config.nasReadTimeout()));
        for (Duration duration : List.of(config.connectTimeout(), config.readTimeout(), config.pcReadTimeout(),
                config.nasReadTimeout(), config.probeTimeout(), config.totalTimeout(), streamTimeout)) {
            if (duration.toMillis() < 2) throw new EmbeddingConfigurationException("Positive embedding timeouts required");
        }
        if (config.totalTimeout().compareTo(streamTimeout) > 0)
            throw new EmbeddingConfigurationException("Embedding budget must not exceed SSE timeout");
        for (Target target : targets) {
            URI uri;
            try { uri = URI.create(target.url()); }
            catch (RuntimeException invalid) { throw new EmbeddingConfigurationException("Invalid embedding endpoint"); }
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                throw new EmbeddingConfigurationException("Invalid embedding endpoint");
        }
    }

    public EmbeddingResponse call(EmbeddingRequest request) {
        RagProgress.checkEmbeddingCancellation();
        if (request.getInstructions() == null || request.getInstructions().isEmpty()
                || request.getInstructions().stream().anyMatch(s -> s == null || s.isBlank()))
            throw new IllegalArgumentException("Non-empty embedding input required");
        if (request.getOptions() != null) {
            if (request.getOptions().getModel() != null && !model.equals(canonical(request.getOptions().getModel()))
                    || request.getOptions().getDimensions() != null && request.getOptions().getDimensions() != 768)
                throw new EmbeddingConfigurationException("Incompatible embedding request options");
        }
        long started = System.nanoTime();
        long budget = Math.min(config.totalTimeout().toMillis(), RagProgress.embeddingTimeLeft(streamTimeout));
        long deadline = started + Duration.ofMillis(budget).toNanos();
        String id = UUID.randomUUID().toString();
        RuntimeException last = null;
        for (int i = 0; i < targets.size(); i++) {
            RagProgress.checkEmbeddingCancellation();
            Target target = targets.get(i);
            long attempt = System.nanoTime();
            String outcome = "failed";
            try {
                var tags = api(target, config.probeTimeout(), deadline).listModels();
                var installed = tags.models().stream().filter(m -> model.equals(canonical(m.name()))).findFirst()
                        .orElseThrow(() -> new EmbeddingConfigurationException("Configured embedding model is not installed"));
                if (!config.expectedDigest().equalsIgnoreCase(installed.digest()))
                    throw new EmbeddingConfigurationException("Embedding model digest mismatch");
                RagProgress.checkEmbeddingCancellation();
                var client = new OllamaEmbeddingModel(api(target, target.timeout(), deadline), options,
                        ObservationRegistry.NOOP, ModelManagementOptions.defaults());
                EmbeddingResponse response = client.call(request);
                RagProgress.checkEmbeddingCancellation();
                validate(response, request.getInstructions().size());
                if (System.nanoTime() > deadline) throw new EmbeddingUnavailableException("Embedding time budget exhausted");
                outcome = "success";
                return response;
            } catch (RuntimeException failure) {
                checkCancellation(failure);
                last = failure;
                log.warn("Embedding attempt id={} provider={} model={} error={}", id, target.name(), model,
                        failure.getClass().getSimpleName());
                if (!recoverable(failure)) throw failure;
                if (i + 1 < targets.size() && System.nanoTime() < deadline)
                    log.info("Embedding fallback id={} from={} to={}", id, target.name(), targets.get(i + 1).name());
            } finally {
                log.info("Embedding id={} provider={} model={} outcome={} attemptMs={} totalMs={}",
                        id, target.name(), model, outcome, elapsed(attempt), elapsed(started));
            }
            if (System.nanoTime() >= deadline) break;
        }
        throw new EmbeddingUnavailableException("Embedding providers unavailable or time budget exhausted", last);
    }

    private OllamaApi api(Target target, Duration readTimeout, long deadline) {
        long remaining = Math.min((deadline - System.nanoTime()) / 1_000_000,
                RagProgress.embeddingTimeLeft(streamTimeout));
        if (remaining < 2) throw new EmbeddingUnavailableException("Embedding time budget exhausted");
        long connect = Math.min(config.connectTimeout().toMillis(), remaining / 2);
        var http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(Duration.ofMillis(connect));
        http.setReadTimeout(Duration.ofMillis(Math.min(readTimeout.toMillis(), remaining - connect)));
        return OllamaApi.builder().baseUrl(target.url())
                .responseErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler())
                .restClientBuilder(RestClient.builder().requestFactory(http)).build();
    }

    private void validate(EmbeddingResponse response, int count) {
        if (response == null || response.getResults() == null || response.getResults().size() != count
                || !model.equals(canonical(response.getMetadata().getModel())))
            throw new EmbeddingConfigurationException("Invalid embedding response model or batch size");
        for (int i = 0; i < count; i++) {
            var result = response.getResults().get(i);
            float[] vector = result.getOutput();
            if (result.getIndex() != i || vector == null || vector.length != 768)
                throw new EmbeddingConfigurationException("Invalid embedding dimensions or batch order");
            boolean nonzero = false;
            for (float value : vector) {
                if (!Float.isFinite(value)) throw new EmbeddingConfigurationException("Non-finite embedding vector");
                nonzero |= value != 0;
            }
            if (!nonzero) throw new EmbeddingConfigurationException("Empty embedding vector");
        }
    }

    private static void checkCancellation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof CancellationException) throw (CancellationException) cause;
            if (cause instanceof InterruptedException || cause instanceof ClosedByInterruptException
                    || cause instanceof InterruptedIOException && !(cause instanceof SocketTimeoutException)) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Embedding cancelled");
            }
        }
        RagProgress.checkEmbeddingCancellation();
    }
    private static boolean recoverable(RuntimeException failure) {
        if (failure instanceof RestClientResponseException http) return RECOVERABLE.contains(http.getStatusCode().value());
        if (!(failure instanceof RestClientException)) return false;
        boolean transport = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException) return false;
            transport |= cause instanceof SocketTimeoutException || cause instanceof SocketException || cause instanceof EOFException;
        }
        return transport;
    }
    private static String canonical(String value) { return value == null ? "" : value.contains(":") ? value : value + ":latest"; }
    private static double elapsed(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
}
