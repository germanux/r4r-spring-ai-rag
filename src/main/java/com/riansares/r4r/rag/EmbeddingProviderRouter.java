package com.riansares.r4r.rag;

import com.riansares.r4r.config.EmbeddingFailoverProperties;
import com.riansares.r4r.config.EmbeddingFailoverProperties.Provider;
import io.micrometer.observation.ObservationRegistry;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Ordered failover with a short negative cache; healthy preferred providers remain first. */
public final class EmbeddingProviderRouter {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingProviderRouter.class);
    private final List<Provider> targets;
    private final EmbeddingFailoverProperties config;
    private final OllamaOptions options;
    private final Duration streamTimeout;
    private final String model;
    private final long unavailableCooldownNanos;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String,Long> unavailableSince = new ConcurrentHashMap<>();

    public int dimensions() { return config.dimensions(); }

    public EmbeddingProviderRouter(EmbeddingFailoverProperties config, OllamaOptions options, Duration streamTimeout) {
        this(config, options, streamTimeout, Duration.ofSeconds(30));
    }
    public EmbeddingProviderRouter(EmbeddingFailoverProperties config, OllamaOptions options, Duration streamTimeout, Duration cooldown) {
        this(config, options, streamTimeout, cooldown, System::nanoTime);
    }
    EmbeddingProviderRouter(EmbeddingFailoverProperties config, OllamaOptions options, Duration streamTimeout,
                            Duration cooldown, LongSupplier clock) {
        if (cooldown == null || cooldown.isNegative() || cooldown.compareTo(Duration.ofMinutes(5)) > 0)
            throw new EmbeddingConfigurationException("Embedding unavailable cooldown must be between 0 and 5 minutes");
        this.unavailableCooldownNanos = cooldown.toNanos();
        this.clock = java.util.Objects.requireNonNull(clock);
        this.config = config;
        this.options = OllamaOptions.fromOptions(options);
        this.model = canonical(options.getModel());
        this.streamTimeout = streamTimeout;
        if (!model.equals("qwen3-embedding:0.6b") || config.dimensions() != 1024
                || config.expectedDigest() == null || !config.expectedDigest().matches("[a-fA-F0-9]{64}"))
            throw new EmbeddingConfigurationException("Compatible Qwen3 embedding model, SHA-256 digest and 1024 dimensions required");
        if (config.providers() == null || config.providers().isEmpty())
            throw new EmbeddingConfigurationException("At least one embedding provider required");
        targets = List.copyOf(config.providers());
        for (Duration duration : List.of(config.connectTimeout(), config.probeTimeout(), config.totalTimeout(), streamTimeout)) {
            if (duration.toMillis() < 2) throw new EmbeddingConfigurationException("Positive embedding timeouts required");
        }
        if (config.totalTimeout().compareTo(streamTimeout) > 0)
            throw new EmbeddingConfigurationException("Embedding budget must not exceed SSE timeout");
        for (Provider target : targets) {
            if (!model.equals(canonical(target.model())))
                throw new EmbeddingConfigurationException("Incompatible embedding provider model");
            if (target.name() == null || !target.name().matches("[a-zA-Z0-9_-]+")
                    || target.readTimeout() == null || target.readTimeout().toMillis() < 2)
                throw new EmbeddingConfigurationException("Provider name and positive read timeout required");
            URI uri;
            try { uri = URI.create(target.baseUrl()); }
            catch (RuntimeException invalid) { throw new EmbeddingConfigurationException("Invalid embedding endpoint"); }
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
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
                    || request.getOptions().getDimensions() != null && request.getOptions().getDimensions() != config.dimensions())
                throw new EmbeddingConfigurationException("Incompatible embedding request options");
        }
        long started = System.nanoTime();
        long budget = Math.min(config.totalTimeout().toMillis(), RagProgress.embeddingTimeLeft(streamTimeout));
        long deadline = started + Duration.ofMillis(budget).toNanos();
        String id = UUID.randomUUID().toString();
        var previous = new java.util.concurrent.atomic.AtomicReference<Provider>();
        try { return ProviderExecution.until(deadline, () -> OrderedProviderFailover.execute(
                targets.stream().map(t -> new OrderedProviderFailover.Target<>(t.name(), t)).toList(), entry -> {
            RagProgress.checkEmbeddingCancellation();
            Provider target = entry.client();
            Long failedAt = unavailableSince.get(target.baseUrl());
            if (failedAt != null && clock.getAsLong() - failedAt < unavailableCooldownNanos) {
                log.info("Embedding id={} provider={} model={} outcome=cooldown remainingMs={}",
                        id, target.name(), model, (unavailableCooldownNanos - (clock.getAsLong() - failedAt)) / 1_000_000);
                return false;
            }
            return true;
        }, target -> {
            previous.set(target);
            long attempt = System.nanoTime();
            String outcome = "failed", phase = "probe";
            log.info("Embedding id={} provider={} host={} model={} outcome=attempt connectTimeoutMs={} probeTimeoutMs={} readTimeoutMs={}",
                    id, target.name(), RagProgress.publicProvider(new GenerationRouter.Provider(target.name(), target.baseUrl(), model)),
                    model, config.connectTimeout().toMillis(), config.probeTimeout().toMillis(), target.readTimeout().toMillis());
            try {
                var tags = api(target, config.probeTimeout()).listModels();
                var installed = tags.models().stream().filter(m -> model.equals(canonical(m.name()))).findFirst()
                        .orElseThrow(() -> new EmbeddingConfigurationException("Configured embedding model is not installed"));
                if (!config.expectedDigest().equalsIgnoreCase(installed.digest()))
                    throw new EmbeddingConfigurationException("Embedding model digest mismatch");
                RagProgress.checkEmbeddingCancellation();
                phase = "inference";
                var client = new OllamaEmbeddingModel(api(target, target.readTimeout()), options,
                        ObservationRegistry.NOOP, ModelManagementOptions.defaults());
                EmbeddingResponse response = client.call(request);
                RagProgress.checkEmbeddingCancellation();
                validate(response, request.getInstructions().size());
                if (System.nanoTime() > deadline) throw new EmbeddingUnavailableException("Embedding time budget exhausted");
                outcome = "success";
                unavailableSince.remove(target.baseUrl());
                RagProgress.embeddingUsed(new GenerationRouter.Provider(target.name(), target.baseUrl(), model));
                return response;
            } catch (RuntimeException failure) {
                checkCancellation(failure);
                boolean cacheFailure = recoverable(failure) && !timedOut(failure);
                log.warn("Embedding attempt id={} provider={} model={} phase={} error={} timeout={} cooldownMs={}",
                        id, target.name(), model, phase, failure.getClass().getSimpleName(), timedOut(failure),
                        cacheFailure ? unavailableCooldownNanos / 1_000_000 : 0);
                if (!recoverable(failure)) throw failure;
                // Loading/queueing can time out while Ollama remains healthy. Do not
                // turn that into 30 seconds of bypassing a now-ready preferred host.
                if (cacheFailure && unavailableCooldownNanos > 0) unavailableSince.put(target.baseUrl(), clock.getAsLong());
                else unavailableSince.remove(target.baseUrl());
                throw failure;
            } finally {
                log.info("Embedding id={} provider={} model={} outcome={} attemptMs={} totalMs={}",
                        id, target.name(), model, outcome, elapsed(attempt), elapsed(started));
            }
        }, ProviderExecution::recoverable, next ->
                log.info("Embedding fallback id={} from={} to={}", id, previous.get().name(), next.client().name())).value());
        } catch (OrderedProviderFailover.Exhausted exhausted) {
            throw new EmbeddingUnavailableException("Embedding providers unavailable or time budget exhausted", exhausted.getCause());
        } catch (ProviderExecution.BudgetExpired exhausted) {
            throw new EmbeddingUnavailableException("Embedding time budget exhausted", exhausted);
        }
    }

    private OllamaApi api(Provider target, Duration readTimeout) {
        // The outer scope already combines the embedding and remaining SSE budgets.
        return OllamaApi.builder().baseUrl(target.baseUrl())
                .responseErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler())
                .restClientBuilder(RestClient.builder().requestFactory(
                        ProviderExecution.http(config.connectTimeout(), readTimeout))).build();
    }

    private void validate(EmbeddingResponse response, int count) {
        if (response == null || response.getResults() == null || response.getResults().size() != count
                || !model.equals(canonical(response.getMetadata().getModel())))
            throw new EmbeddingConfigurationException("Invalid embedding response model or batch size");
        for (int i = 0; i < count; i++) {
            var result = response.getResults().get(i);
            float[] vector = result.getOutput();
            if (result.getIndex() != i || vector == null || vector.length != config.dimensions())
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
        ProviderExecution.checkCancellation(failure);
        RagProgress.checkEmbeddingCancellation();
    }
    private static boolean recoverable(RuntimeException failure) {
        return ProviderExecution.recoverable(failure);
    }
    private static boolean timedOut(Throwable failure) {
        if (failure instanceof ProviderExecution.BudgetExpired) return true;
        if (failure instanceof RestClientResponseException http && http.getStatusCode().value() == 408) return true;
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof SocketTimeoutException) return true;
        return false;
    }
    private static String canonical(String value) { return value == null ? "" : value.contains(":") ? value : value + ":latest"; }
    private static double elapsed(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
}
