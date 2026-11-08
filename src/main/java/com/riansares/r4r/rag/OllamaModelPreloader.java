package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.config.GenerationProviderProperties;
import com.riansares.r4r.config.EmbeddingFailoverProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded, independent local-only warmups. Runtime observations never trigger inference. */
@Component
public class OllamaModelPreloader implements AutoCloseable {
    public record ModelStatus(String id, String model, String state, Instant checkedAt, String error, boolean refreshing,
                              Instant warmupCompletedAt, boolean memoryObserved) {
        public ModelStatus(String id, String model, String state, Instant checkedAt, String error, boolean refreshing) {
            this(id, model, state, checkedAt, error, refreshing, null, false);
        }
    }
    public record WarmupResult(List<String> accepted, List<String> skipped, long retryAfterSeconds) { }
    public record LoadedModel(String name, String parameterSize, Long sizeBytes, Long vramBytes) { }
    public record MemoryState(boolean available, List<LoadedModel> models) { }
    record Target(String id, GenerationRouter.Provider provider, OllamaApi api, Runnable warmup) { }
    private static final class Job {
        final Target target;
        boolean running, warming, pendingWarmup;
        Instant warmupCompletedAt;
        long nextAttempt, nextObservation;
        ModelStatus status;
        MemoryState memory = new MemoryState(false, List.of());
        Job(Target target) {
            this.target = target;
            status = new ModelStatus(target.id(), target.provider().model(), "unknown", null, null, false);
        }
    }
    @Value("${r4r.rag.warmup.on-startup:true}") private boolean onStartup = true;
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
    private final List<Job> jobs;
    private final Duration minInterval;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final ExecutorService executor = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "r4r-local-model");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public OllamaModelPreloader(ObjectMapper mapper, OllamaEmbeddingProperties embeddings, OllamaChatProperties chat,
            GenerationProviderProperties generation, EmbeddingFailoverProperties embedding,
            @Value("${r4r.rag.warmup.min-interval:1m}") Duration minInterval) {
        this(targets(generation, embedding, embeddings, chat), mapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(), minInterval);
    }

    OllamaModelPreloader(List<Target> targets, ObjectMapper mapper, HttpClient http, Duration minInterval) {
        if (minInterval.isNegative() || minInterval.isZero()) throw new IllegalArgumentException("Positive warmup interval required");
        this.jobs = targets.stream().map(Job::new).toList();
        this.mapper = mapper;
        this.http = http;
        this.minInterval = minInterval;
    }

    private static List<Target> targets(GenerationProviderProperties generation, EmbeddingFailoverProperties embedding,
            OllamaEmbeddingProperties embeddings, OllamaChatProperties chat) {
        List<Target> targets = new ArrayList<>();
        for (var configured : generation.providers()) {
            var provider = new GenerationRouter.Provider(configured.name(), configured.baseUrl(), configured.model());
            var api = api(provider.baseUrl(), configured.readTimeout());
            targets.add(new Target(displayId(provider), provider, api(provider.baseUrl(), generation.probeTimeout()), () -> {
                var result = api.chat(OllamaApi.ChatRequest.builder(provider.model()).messages(List.of())
                        .stream(false).keepAlive("3h").options(chat.getOptions()).build());
                if (result == null || !Boolean.TRUE.equals(result.done())) throw new IllegalStateException("Empty warmup response");
            }));
        }
        var preferredEmbedding=embedding.providers().getFirst();
        String embeddingUrl=preferredEmbedding.baseUrl();
        Duration embeddingTimeout=preferredEmbedding.readTimeout();
        var options = embeddings.getOptions();
        var api = api(embeddingUrl, embeddingTimeout);
        var provider = new GenerationRouter.Provider("Embeddings", embeddingUrl, options.getModel());
        targets.add(new Target("embeddings", provider, api(provider.baseUrl(), Duration.ofSeconds(2)), () -> {
            var result = api.embed(new OllamaApi.EmbeddingsRequest(options.getModel(), List.of(),
                    OllamaEmbeddingModel.DurationParser.parse(options.getKeepAlive()),
                    OllamaOptions.filterNonSupportedFields(options.toMap()), options.getTruncate()));
            if (result == null || result.embeddings() == null) throw new IllegalStateException("Empty warmup response");
        }));
        return targets;
    }

    private static String displayId(GenerationRouter.Provider provider) {
        // Known inventory identifies actual hosts, never the list position or model name.
        return switch (URI.create(provider.baseUrl()).getHost()) {
            case "192.168.1.207" -> "desktop";
            case "192.168.1.209" -> "laptop";
            default -> provider.name();
        };
    }

    private static OllamaApi api(String url, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive warmup timeout required");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(timeout);
        return OllamaApi.builder().baseUrl(url).restClientBuilder(RestClient.builder().requestFactory(factory)).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void preloadAfterStartup() { if (onStartup) requestWarmup(); }

    public WarmupResult requestWarmup() {
        List<String> accepted = new ArrayList<>(), skipped = new ArrayList<>();
        long retry = 0;
        for (Job job : jobs) synchronized (job) {
            long now = System.nanoTime();
            if (closed.get() || job.warming || job.pendingWarmup || now < job.nextAttempt) {
                skipped.add(job.target.id());
                retry = Math.max(retry, Math.max(1, (job.nextAttempt - now + 999_999_999L) / 1_000_000_000L));
                continue;
            }
            job.nextAttempt = now + minInterval.toNanos();
            accepted.add(job.target.id());
            retry = Math.max(retry, Math.max(1, (minInterval.toMillis() + 999) / 1000));
            if (job.running) job.pendingWarmup = true; // Reserve exactly one warmup behind the observation.
            else {
                job.running = true;
                job.warming = true;
                job.status = status(job, "loading", null);
                executor.execute(() -> run(job, true));
            }
        }
        return new WarmupResult(List.copyOf(accepted), List.copyOf(skipped), retry);
    }

    /** Cached read; at most one bounded observation per provider every ten seconds. */
    public List<ModelStatus> modelStates() {
        List<ModelStatus> states = new ArrayList<>();
        for (Job job : jobs) synchronized (job) {
            if (!closed.get() && !job.running && System.nanoTime() >= job.nextObservation) {
                job.running = true;
                job.warming = false;
                executor.execute(() -> run(job, false));
            }
            var current = job.status;
            states.add(new ModelStatus(current.id(), current.model(), current.state(), current.checkedAt(), current.error(), job.running && !job.warming, job.warmupCompletedAt, job.memory.available()));
        }
        return List.copyOf(states);
    }

    private void run(Job job, boolean warmup) {
        while (true) {
            observeOrWarm(job, warmup);
            synchronized (job) {
                if (job.pendingWarmup && !closed.get()) {
                    job.pendingWarmup = false;
                    job.warming = true;
                    job.status = status(job, "loading", null);
                    warmup = true; // Same worker; no additional queued tasks or HTTP waits.
                } else {
                    job.running = false;
                    job.warming = false;
                    job.pendingWarmup = false;
                    job.nextObservation = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                    return;
                }
            }
        }
    }

    private void observeOrWarm(Job job, boolean warmup) {
        try {
            boolean installed = job.target.api().listModels().models().stream()
                    .anyMatch(m -> canonical(m.name()).equals(canonical(job.target.provider().model())));
            if (!installed) {
                synchronized (job) { job.memory = new MemoryState(false, List.of()); job.status = status(job, "unreachable", "model_missing"); }
                return; // Never pull a missing model automatically.
            }
            MemoryState memory = loadedModels(job.target.provider().baseUrl());
            boolean loaded = containsModel(memory, job.target.provider().model());
            if (warmup && !loaded && !closed.get()) {
                job.target.warmup().run();
                synchronized (job) { job.warmupCompletedAt = Instant.now(); }
                memory = loadedModels(job.target.provider().baseUrl());
                loaded = containsModel(memory, job.target.provider().model());
            }
            synchronized (job) {
                job.memory = memory;
                job.status = status(job, memory.available() ? loaded ? "loaded" : "unloaded" : "available", null);
            }
        } catch (Exception ex) {
            boolean timeout = false;
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException) timeout = true;
            }
            synchronized (job) {
                job.memory = new MemoryState(false, List.of());
                job.status = status(job, "unreachable", timeout ? "timeout" : "provider_unavailable");
            }
        }
    }
    private static boolean containsModel(MemoryState memory, String model) {
        return memory.models().stream().anyMatch(m -> canonical(m.name()).equals(canonical(model)));
    }

    private static ModelStatus status(Job job, String state, String error) {
        return new ModelStatus(job.target.id(), job.target.provider().model(), state, Instant.now(), error, false, job.warmupCompletedAt, job.memory.available());
    }
    private static String canonical(String name) { return name.contains(":") ? name : name + ":latest"; }

    private boolean matches(Job job, GenerationRouter.Provider provider) {
        var configured = job.target.provider();
        return configured.name().equals(provider.name())
                && configured.baseUrl().replaceAll("/+$", "").equals(provider.baseUrl().replaceAll("/+$", ""))
                && canonical(configured.model()).equals(canonical(provider.model()));
    }
    public ModelStatus statusFor(GenerationRouter.Provider provider) {
        for (Job job : jobs) if (matches(job, provider)) synchronized (job) { return job.status; }
        return null;
    }
    public MemoryState memoryFor(GenerationRouter.Provider provider) {
        for (Job job : jobs) if (matches(job, provider)) synchronized (job) { return job.memory; }
        return new MemoryState(false, List.of());
    }

    // Existing /api/ps telemetry, cached independently for each local provider.
    private MemoryState loadedModels(String baseUrl) {
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/api/ps"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return new MemoryState(false, List.of());
            List<LoadedModel> result = new ArrayList<>();
            var models = mapper.readTree(response.body()).path("models");
            if (!models.isArray()) return new MemoryState(false, List.of());
            for (var model : models) {
                result.add(new LoadedModel(model.path("name").asText("unknown"),
                        model.path("details").path("parameter_size").asText("unknown"),
                        reportedBytes(model.path("size")), reportedBytes(model.path("size_vram"))));
            }
            return new MemoryState(true, List.copyOf(result));
        } catch (Exception ignored) { return new MemoryState(false, List.of()); }
    }
    private static Long reportedBytes(com.fasterxml.jackson.databind.JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0 ? value.longValue() : null;
    }

    @Override @PreDestroy public void close() {
        closed.set(true);
        for (Job job : jobs) synchronized (job) { job.pendingWarmup = false; }
        executor.shutdownNow();
    }
}
