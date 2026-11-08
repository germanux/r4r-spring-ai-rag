package com.riansares.r4r.rag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One request, explicitly passed on its worker. Only the synchronous VectorStore bridge is scoped. */
public final class RagProgress {
    private static final ThreadLocal<RagProgress> EMBEDDING_SCOPE = new ThreadLocal<>();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RagProgress.class);
    private final long started = System.nanoTime();
    private final Consumer<Map<String, Object>> sink;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile Double embeddingMs, retrievalIncludingEmbeddingMs, generationMs;
    private GenerationRouter.Provider used;
    private volatile Integer documentsFound;

    public RagProgress(Consumer<Map<String, Object>> sink) { this.sink = sink; }
    public static RagProgress silent() { return new RagProgress(event -> { }); }
    public void cancel() { cancelled.set(true); }
    public void checkCancelled() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CancellationException("RAG request cancelled");
    }
    public Integer documentsFound() { return documentsFound; }
    public long elapsedMs() { return (System.nanoTime() - started) / 1_000_000; }
    public GenerationRouter.Provider usedProvider() { return used; }
    public void usedProvider(GenerationRouter.Provider provider) { this.used = provider; }
    public void retrievalMs(double ms) { retrievalIncludingEmbeddingMs = ms; }
    public void generationMs(double ms) { generationMs = ms; }
    public Map<String, Object> metrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("embeddingMs", embeddingMs);
        metrics.put("vectorSearchMs", null); // No isolated SQL timer in Spring AI 1.0 PgVectorStore.
        metrics.put("retrievalIncludingEmbeddingMs", retrievalIncludingEmbeddingMs);
        metrics.put("generationMs", generationMs);
        metrics.put("totalMs", (System.nanoTime() - started) / 1_000_000.0);
        return metrics;
    }
    public Map<String, Object> event(String stage, GenerationRouter.Provider provider, Integer documents, Double durationMs, String message) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("stage", stage);
        event.put("elapsedMs", elapsedMs());
        if (provider != null) {
            event.put("provider", publicProvider(provider));
            event.put("model", provider.model());
        }
        if (documents != null) event.put("documentsFound", documents);
        if (durationMs != null) event.put("durationMs", durationMs);
        if (message != null) event.put("message", message);
        return event;
    }
    public void emit(String stage) { emit(stage, null, null, null, null); }
    public void emit(String stage, GenerationRouter.Provider provider, Integer documents, Double durationMs, String message) {
        checkCancelled();
        if ("RETRIEVAL_COMPLETED".equals(stage)) documentsFound = documents;
        sink.accept(event(stage, provider, documents, durationMs, message));
        if ("VALIDATION_COMPLETED".equals(stage)) log.info("RAG measured metrics={}", metrics());
        checkCancelled();
    }
    public static String publicProvider(GenerationRouter.Provider provider) {
        if ("OpenAI".equals(provider.name())) return "OpenAI";
        return provider.model().toLowerCase(java.util.Locale.ROOT).startsWith("qwen2.5")
                || provider.name().contains("ASUS") ? "Portátil ASUS" : "PC principal";
    }

    public <T> T duringRetrieval(Supplier<T> action) {
        RagProgress previous = EMBEDDING_SCOPE.get();
        EMBEDDING_SCOPE.set(this);
        try { checkCancelled(); return action.get(); }
        finally { if (previous == null) EMBEDDING_SCOPE.remove(); else EMBEDDING_SCOPE.set(previous); }
    }
    static void checkEmbeddingCancellation() {
        RagProgress progress = EMBEDDING_SCOPE.get();
        if (progress != null) progress.checkCancelled();
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Embedding cancelled");
    }
    static long embeddingTimeLeft(java.time.Duration timeout) {
        RagProgress progress = EMBEDDING_SCOPE.get();
        return progress == null ? Long.MAX_VALUE : Math.max(0, timeout.toMillis() - progress.elapsedMs());
    }
    public static <T> T observeEmbedding(String model, Supplier<T> action) {
        RagProgress progress = EMBEDDING_SCOPE.get();
        if (progress == null) return action.get(); // Ingestion, warmup and other callers are unaffected.
        var event = progress.event("EMBEDDING_STARTED", null, null, null, null);
        event.put("model", model);
        progress.checkCancelled();
        progress.sink.accept(event);
        progress.checkCancelled();
        long start = System.nanoTime();
        T result;
        double duration;
        try { result = action.get(); }
        finally {
            duration = (System.nanoTime() - start) / 1_000_000.0;
            progress.embeddingMs = (progress.embeddingMs == null ? 0 : progress.embeddingMs) + duration;
        }
        var completed = progress.event("EMBEDDING_COMPLETED", null, null, duration, null);
        completed.put("model", model);
        progress.checkCancelled();
        progress.sink.accept(completed);
        progress.checkCancelled();
        return result;
    }
}
