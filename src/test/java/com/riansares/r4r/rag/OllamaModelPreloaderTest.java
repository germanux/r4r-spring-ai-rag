package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingProperties;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Real Spring AI clients against simulated Ollama servers; no live models or database. */
class OllamaModelPreloaderTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void startupWarmsAllThreeInParallelAndDeduplicatesConcurrentRequests() throws Exception {
        var entered = new CountDownLatch(3);
        var release = new CountDownLatch(1);
        try (var pc = new Server("qwen3:8b", entered, release);
             var laptop = new Server("qwen2.5:7b", entered, release);
             var embedding = new Server("qwen3-embedding:0.6b", entered, release);
             var preloader = preloader(pc, laptop, embedding, Duration.ofSeconds(2), Duration.ofMinutes(1))) {
            long start = System.nanoTime();
            preloader.preloadAfterStartup();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
            assertThat(entered.await(3, TimeUnit.SECONDS)).as("Independent parallel tasks").isTrue();
            assertThat(preloader.modelStates()).allMatch(s -> s.state().equals("loading"));
            try (var callers = Executors.newFixedThreadPool(8)) {
                var tasks = java.util.stream.IntStream.range(0, 20)
                        .<java.util.concurrent.Callable<OllamaModelPreloader.WarmupResult>>mapToObj(i -> preloader::requestWarmup).toList();
                for (var result : callers.invokeAll(tasks)) assertThat(result.get().accepted()).isEmpty();
            }
            release.countDown();
            await(() -> preloader.modelStates().stream().allMatch(s -> s.state().equals("loaded")));
            assertThat(pc.posts).hasSize(1);
            assertThat(laptop.posts).hasSize(1);
            assertThat(embedding.posts).hasSize(1);
            assertThat(mapper.readTree(pc.posts.getFirst()).path("keep_alive").asText()).isEqualTo("8h");
            var laptopBody = mapper.readTree(laptop.posts.getFirst());
            assertThat(laptopBody.path("messages")).isEmpty();
            assertThat(laptopBody.path("model").asText()).isEqualTo("qwen2.5:7b");
            assertThat(laptopBody.path("options").path("num_ctx").asInt()).isEqualTo(4096);
            var embedBody = mapper.readTree(embedding.posts.getFirst());
            assertThat(embedBody.path("input")).isEmpty();
            assertThat(embedBody.path("keep_alive").asDouble()).isEqualTo(28800); // Spring AI serializes Duration in seconds.
            var repeated = preloader.requestWarmup();
            assertThat(repeated.accepted()).isEmpty();
            assertThat(repeated.skipped()).hasSize(3);
            assertThat(repeated.retryAfterSeconds()).isPositive();
        } finally { release.countDown(); }
    }

    @Test void startupLoadsModelsOnDifferentOllamaInstancesInParallel() throws Exception {
        var entered = new CountDownLatch(5);
        var release = new CountDownLatch(1);
        try (var pc = new Server("qwen3:8b", entered, release);
             var laptop = new Server("qwen2.5:7b", entered, release);
             var lpEmbedding = new Server("qwen3-embedding:0.6b", entered, release);
             var pcEmbedding = new Server("qwen3-embedding:0.6b", entered, release);
             var nasEmbedding = new Server("qwen3-embedding:0.6b", entered, release);
             var preloader = preloader(pc, laptop, List.of(lpEmbedding, pcEmbedding, nasEmbedding),
                     Duration.ofSeconds(2), Duration.ofMinutes(1), Duration.ofSeconds(2))) {
            preloader.preloadAfterStartup();
            assertThat(entered.await(3, TimeUnit.SECONDS)).as("All five loads start without waiting for another model").isTrue();
            assertThat(preloader.requestWarmup().accepted()).isEmpty();
            release.countDown();
            await(() -> preloader.modelStates().stream().allMatch(s -> s.state().equals("loaded")));
            for (var embedding : List.of(lpEmbedding, pcEmbedding, nasEmbedding)) {
                assertThat(embedding.posts).hasSize(1);
                var body = mapper.readTree(embedding.posts.getFirst());
                assertThat(body.path("input")).isEmpty();
                assertThat(body.path("keep_alive").asDouble()).isEqualTo(28800);
            }
        } finally { release.countDown(); }
    }

    @Test void coldEmbeddingLoadHasSeparateBudgetFromQueryReadTimeout() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pc = new Server("qwen3:8b"); var laptop = new Server("qwen2.5:7b");
             var embedding = new Server("qwen3-embedding:0.6b", entered, release);
             var preloader = preloader(pc, laptop, List.of(embedding), Duration.ofMillis(150),
                     Duration.ofMinutes(1), Duration.ofSeconds(2))) {
            preloader.requestWarmup();
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(350); // Longer than query read timeout, shorter than the dedicated load budget.
            release.countDown();
            await(() -> state(preloader, "embeddings").equals("loaded"));
            assertThat(embedding.posts).hasSize(1);
        } finally { release.countDown(); }
    }

    @Test void sharedSingleSlotInstancesProtectGenerationAndPrepareLaptopEmbeddingsWithoutThrashing() throws Exception {
        try (var pc = new Server("qwen3:8b"); var laptop = new Server("qwen2.5:7b");
             var nas = new Server("qwen3-embedding:0.6b")) {
            pc.secondaryModel = laptop.secondaryModel = "qwen3-embedding:0.6b";
            try (var preloader = preloader(pc, laptop, List.of(laptop, pc, nas), Duration.ofSeconds(1),
                    Duration.ofMillis(50), Duration.ofSeconds(2))) {
                preloader.preloadAfterStartup();
                try (var tabs = Executors.newFixedThreadPool(8)) {
                    tabs.invokeAll(java.util.stream.IntStream.range(0,20)
                            .<java.util.concurrent.Callable<OllamaModelPreloader.WarmupResult>>mapToObj(i -> preloader::requestWarmup).toList());
                }
                await(() -> preloader.modelStates().stream().allMatch(s -> s.checkedAt()!=null && !s.state().equals("loading")));
                assertThat(pc.residentModel).isEqualTo("qwen3:8b");
                assertThat(laptop.residentModel).isEqualTo("qwen3-embedding:0.6b");
                assertThat(pc.posts).hasSize(1); assertThat(laptop.posts).hasSize(1);
                assertThat(pc.evictions.get()).isZero(); assertThat(laptop.evictions.get()).isZero();
                assertThat(pc.maxOperations.get()).isEqualTo(1); assertThat(laptop.maxOperations.get()).isEqualTo(1);

                Thread.sleep(60);
                preloader.requestWarmup();
                await(() -> preloader.modelStates().stream().noneMatch(s -> s.state().equals("loading")));
                for (int i=0;i<10;i++) preloader.modelStates();
                assertThat(pc.posts).hasSize(1); assertThat(laptop.posts).hasSize(1);
                assertThat(pc.evictions.get()).isZero(); assertThat(laptop.evictions.get()).isZero();

                // Real Spring AI routing through the same limited servers after warmup.
                var config = new com.riansares.r4r.config.EmbeddingFailoverProperties(List.of(
                        new com.riansares.r4r.config.EmbeddingFailoverProperties.Provider("laptop",laptop.url(),Duration.ofMillis(1500)),
                        new com.riansares.r4r.config.EmbeddingFailoverProperties.Provider("desktop",pc.url(),Duration.ofMillis(1500)),
                        new com.riansares.r4r.config.EmbeddingFailoverProperties.Provider("nas",nas.url(),Duration.ofSeconds(30))),
                        Duration.ofMillis(400),Duration.ofMillis(600),Duration.ofSeconds(35),1024,EmbeddingProviderFailoverTest.DIGEST);
                var options=org.springframework.ai.ollama.api.OllamaOptions.builder().model("qwen3-embedding:0.6b").keepAlive("8h").build();
                var observed=new ObservedEmbeddingModel(org.springframework.ai.ollama.api.OllamaApi.builder().baseUrl(laptop.url()).build(),
                        options,new EmbeddingProviderRouter(config,options,Duration.ofSeconds(35)));
                var first=RagProgress.silent(); first.duringRetrieval(() -> observed.embed("test query"));
                assertThat(first.usedEmbeddingProvider().baseUrl()).isEqualTo(laptop.url());
                laptop.server.stop(0);
                var second=RagProgress.silent(); second.duringRetrieval(() -> observed.embed("fallback query"));
                assertThat(second.usedEmbeddingProvider().baseUrl()).isEqualTo(pc.url());
                assertThat(nas.posts).hasSize(1); // Only the warmup; neither query falls back to the NAS.
            }
        }
    }

    @Test void aResidentFallbackLanguageModelIsNotEvictedByPageOpeningOrObservation() throws Exception {
        try (var pc=new Server("qwen3:8b"); var laptop=new Server("qwen2.5:7b"); var nas=new Server("qwen3-embedding:0.6b")) {
            pc.secondaryModel=laptop.secondaryModel="qwen3-embedding:0.6b";
            pc.loaded=laptop.loaded=true;
            try (var preloader=preloader(pc,laptop,List.of(laptop,pc,nas),Duration.ofSeconds(1),Duration.ofMinutes(1),Duration.ofSeconds(2))) {
                preloader.requestWarmup();
                await(() -> preloader.modelStates().stream().allMatch(s -> s.checkedAt()!=null && !s.state().equals("loading")));
                assertThat(pc.posts).isEmpty(); assertThat(laptop.posts).isEmpty();
                assertThat(pc.evictions.get()).isZero(); assertThat(laptop.evictions.get()).isZero();
                assertThat(state(preloader,"embeddings")).isEqualTo("unloaded");
                assertThat(state(preloader,"laptop")).isEqualTo("loaded");
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void oneDisconnectedProviderNeverBlocksOtherModels(boolean desktopDown) throws Exception {
        try (var pc = new Server("qwen3:8b"); var laptop = new Server("qwen2.5:7b");
             var embedding = new Server("qwen3-embedding:0.6b")) {
            if (desktopDown) pc.server.stop(0); else laptop.server.stop(0);
            try (var preloader = preloader(pc, laptop, embedding, Duration.ofSeconds(1), Duration.ofMinutes(1))) {
                preloader.requestWarmup();
                await(() -> state(preloader, "desktop").equals(desktopDown ? "unreachable" : "loaded") && state(preloader, "laptop").equals(desktopDown ? "loaded" : "unreachable") && state(preloader, "embeddings").equals("loaded"));
                assertThat((desktopDown ? laptop : pc).posts).hasSize(1);
                assertThat(embedding.posts).hasSize(1);
            }
        }
    }

    @Test void bothDisconnectedStillPreparesEmbeddingsAndNeverInvokesOpenAi() throws Exception {
        try (var pc = new Server("qwen3:8b"); var laptop = new Server("qwen2.5:7b"); var embedding = new Server("qwen3-embedding:0.6b")) {
            pc.server.stop(0); laptop.server.stop(0);
            try (var preloader = preloader(pc, laptop, embedding, Duration.ofSeconds(1), Duration.ofMinutes(1))) {
                preloader.requestWarmup();
                await(() -> state(preloader, "desktop").equals("unreachable") && state(preloader, "laptop").equals("unreachable") && state(preloader, "embeddings").equals("loaded"));
                assertThat(mapper.writeValueAsString(preloader.modelStates())).doesNotContain("127.0.0.1", "http://", "OpenAI");
            }
        }
    }

    @Test void timeoutIsIsolatedAndCanRetryOnlyAfterCooldown() throws Exception {
        var release = new CountDownLatch(1);
        try (var pc = new Server("qwen3:8b", new CountDownLatch(1), release);
             var laptop = new Server("qwen2.5:7b"); var embedding = new Server("qwen3-embedding:0.6b");
             var preloader = preloader(pc, laptop, embedding, Duration.ofMillis(150), Duration.ofMillis(500))) {
            preloader.requestWarmup();
            await(() -> state(preloader, "desktop").equals("unreachable") && state(preloader, "laptop").equals("loaded"));
            assertThat(preloader.modelStates().getFirst().error()).isEqualTo("timeout");
            assertThat(preloader.requestWarmup().accepted()).isEmpty();
            release.countDown();
            Thread.sleep(500);
            assertThat(preloader.requestWarmup().accepted()).contains("desktop", "laptop", "embeddings");
            await(() -> state(preloader, "desktop").equals("loaded"));
        } finally { release.countDown(); }
    }

    @Test void runtimeReadsObserveLoadedAndUnloadedWithoutWarmupOrModelDownloads() throws Exception {
        try (var pc = new Server("qwen3:8b"); var laptop = new Server("qwen2.5:7b"); var embedding = new Server("qwen3-embedding:0.6b");
             var preloader = preloader(pc, laptop, embedding, Duration.ofSeconds(1), Duration.ofMinutes(1))) {
            pc.loaded = true;
            assertThat(preloader.modelStates()).allMatch(s -> s.state().equals("unknown"));
            await(() -> state(preloader, "desktop").equals("loaded") && state(preloader, "laptop").equals("unloaded"));
            assertThat(pc.posts).isEmpty(); assertThat(laptop.posts).isEmpty(); assertThat(embedding.posts).isEmpty();
            assertThat(preloader.memoryFor(provider("PC", pc)).models()).singleElement().satisfies(m -> {
                assertThat(m.sizeBytes()).isEqualTo(5295200000L);
                assertThat(m.vramBytes()).isEqualTo(4294967296L);
            });
        }
    }

    @Test void missingModelsAndMissingTelemetryAreNotInventedAsLoadedOrZero() throws Exception {
        try (var pc = new Server("qwen3:8b"); var laptop = new Server("qwen2.5:7b"); var embedding = new Server("qwen3-embedding:0.6b")) {
            pc.installed = false;
            laptop.psBody = "{\"models\":[{\"name\":\"qwen2.5:7b\",\"size\":-1},{\"name\":\"zero\",\"size\":0,\"size_vram\":0}]}";
            embedding.psStatus = 503;
            try (var preloader = preloader(pc, laptop, embedding, Duration.ofSeconds(1), Duration.ofMinutes(1))) {
                preloader.modelStates();
                await(() -> state(preloader, "desktop").equals("unreachable") && state(preloader, "laptop").equals("loaded") && state(preloader, "embeddings").equals("available"));
                assertThat(pc.posts).isEmpty();
                var models = preloader.memoryFor(provider("ASUS", laptop)).models();
                assertThat(models.getFirst().sizeBytes()).isNull(); assertThat(models.getFirst().vramBytes()).isNull();
                assertThat(models.get(1).sizeBytes()).isZero(); assertThat(models.get(1).vramBytes()).isZero();
            }
        }
    }

    @Test void runtimeFirstPreservesOnePendingWarmupAcrossConcurrentTabs() throws Exception {
        try (var pc = new Server("qwen3:8b"); var laptop = new Server("qwen2.5:7b"); var embedding = new Server("qwen3-embedding:0.6b");
             var preloader = preloader(pc, laptop, embedding, Duration.ofSeconds(2), Duration.ofMinutes(1))) {
            pc.observing = new CountDownLatch(1);
            pc.releaseObservation = new CountDownLatch(1);
            preloader.modelStates();
            assertThat(pc.observing.await(1, TimeUnit.SECONDS)).isTrue();
            var accepted = preloader.requestWarmup();
            assertThat(accepted.accepted()).contains("desktop");
            try (var tabs = Executors.newFixedThreadPool(8)) {
                var tasks = java.util.stream.IntStream.range(0, 20)
                        .<java.util.concurrent.Callable<OllamaModelPreloader.WarmupResult>>mapToObj(i -> preloader::requestWarmup).toList();
                for (var result : tabs.invokeAll(tasks)) assertThat(result.get().accepted()).doesNotContain("desktop");
            }
            pc.releaseObservation.countDown();
            await(() -> state(preloader, "desktop").equals("loaded"));
            assertThat(pc.posts).hasSize(1);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void completedWarmupRequiresPsEvidenceBeforeReportingLoaded(boolean psAvailable) throws Exception {
        try (var pc = new Server("qwen3:8b"); var laptop = new Server("qwen2.5:7b"); var embedding = new Server("qwen3-embedding:0.6b");
             var preloader = preloader(pc, laptop, embedding, Duration.ofSeconds(1), Duration.ofMinutes(1))) {
            pc.loaded = true;
            embedding.psStatus = psAvailable ? 200 : 503;
            embedding.psBody = "{\"models\":[]}";
            preloader.requestWarmup();
            await(() -> state(preloader, "desktop").equals("loaded") && state(preloader, "embeddings").equals(psAvailable ? "unloaded" : "available"));
            assertThat(pc.posts).isEmpty();
            var status = preloader.statusFor(provider("Embeddings", embedding));
            if (psAvailable) assertThat(status.warmupCompletedAt()).isNotNull();
            else assertThat(status.warmupCompletedAt()).isNull();
            assertThat(status.memoryObserved()).isEqualTo(psAvailable);
            assertThat(preloader.statusFor(provider("PC", pc)).warmupCompletedAt()).isNull();
            assertThat(preloader.statusFor(provider("PC", pc)).memoryObserved()).isTrue();
        }
    }

    @Test void sameModelOnDifferentHostsKeepsIndependentIdentityAndRecovery() throws Exception {
        try (var pc = new Server("shared:latest"); var laptop = new Server("shared:latest"); var embedding = new Server("qwen3-embedding:0.6b");
             var preloader = preloader(pc, laptop, embedding, Duration.ofSeconds(1), Duration.ofMillis(100))) {
            pc.loaded = true;
            laptop.tagsStatus = 503;
            preloader.requestWarmup();
            await(() -> state(preloader, "desktop").equals("loaded") && state(preloader, "laptop").equals("unreachable"));
            assertThat(preloader.statusFor(provider("PC", pc)).id()).isEqualTo("desktop");
            assertThat(preloader.statusFor(provider("ASUS", laptop)).state()).isEqualTo("unreachable");
            laptop.tagsStatus = 200;
            Thread.sleep(110);
            preloader.requestWarmup();
            await(() -> state(preloader, "laptop").equals("loaded"));
            assertThat(laptop.posts).hasSize(1);
            assertThat(pc.posts).isEmpty();
        }
    }

    private OllamaModelPreloader preloader(Server pc, Server laptop, Server embedding, Duration timeout, Duration cooldown) {
        return preloader(pc, laptop, List.of(embedding), timeout, cooldown, timeout);
    }
    private OllamaModelPreloader preloader(Server pc, Server laptop, List<Server> embeddingServers,
                                         Duration timeout, Duration cooldown, Duration embeddingWarmupTimeout) {
        Server embedding = embeddingServers.getFirst();
        ChatModel remote = mock(ChatModel.class);
        var router = new GenerationRouter(List.of(
                new GenerationRouter.Target(provider("PC", pc), mock(ChatModel.class), () -> true),
                new GenerationRouter.Target(provider("ASUS", laptop), mock(ChatModel.class), () -> true),
                new GenerationRouter.Target(new GenerationRouter.Provider("OpenAI", "https://api.openai.com/v1", "gpt-6-luna"), remote, () -> { throw new AssertionError("Remote probe during warmup"); })));
        var properties = new OllamaEmbeddingProperties();
        properties.getOptions().setModel("qwen3-embedding:0.6b");
        properties.getOptions().setKeepAlive("8h");
        // No reference to remote is retained by the preloader, so it cannot call it.
        assertThat(router.localProviders()).hasSize(2);
        verifyNoInteractions(remote);
        var chat = new org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties();
        chat.getOptions().setNumCtx(4096);
        chat.getOptions().setKeepAlive("8h");
        var generation=new com.riansares.r4r.config.GenerationProviderProperties(List.of(
                new com.riansares.r4r.config.GenerationProviderProperties.Provider("desktop",pc.url(),pc.model,timeout),
                new com.riansares.r4r.config.GenerationProviderProperties.Provider("laptop",laptop.url(),laptop.model,timeout)),
                timeout,timeout);
        var embeddingConfig=new com.riansares.r4r.config.EmbeddingFailoverProperties(embeddingServers.stream().map(server ->
                new com.riansares.r4r.config.EmbeddingFailoverProperties.Provider("embedding-"+embeddingServers.indexOf(server),server.url(),timeout)).toList(),
                timeout,timeout,timeout,1024,"test-unused");
        return new OllamaModelPreloader(mapper, properties, chat, generation,embeddingConfig,cooldown,embeddingWarmupTimeout);
    }
    private static GenerationRouter.Provider provider(String name, Server server) {
        return new GenerationRouter.Provider(name.equals("PC") ? "desktop" : name.equals("ASUS") ? "laptop" : name, server.url(), server.model);
    }
    private static String state(OllamaModelPreloader preloader, String id) {
        return preloader.modelStates().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow().state();
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }
    private final class Server implements AutoCloseable {
        final HttpServer server;
        final ExecutorService executor = Executors.newCachedThreadPool();
        final String model;
        final List<String> posts = new CopyOnWriteArrayList<>();
        volatile boolean loaded, installed = true;
        volatile int psStatus = 200, tagsStatus = 200;
        volatile String psBody;
        volatile String secondaryModel, residentModel;
        final java.util.concurrent.atomic.AtomicInteger operations = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger maxOperations = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger evictions = new java.util.concurrent.atomic.AtomicInteger();
        volatile CountDownLatch observing = new CountDownLatch(0), releaseObservation = new CountDownLatch(0);
        Server(String model) throws Exception { this(model, new CountDownLatch(0), new CountDownLatch(0)); }
        Server(String model, CountDownLatch entered, CountDownLatch release) throws Exception {
            this.model = model;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                maxOperations.accumulateAndGet(operations.incrementAndGet(), Math::max);
                try {
                    if (secondaryModel != null) Thread.sleep(10);
                    String path = exchange.getRequestURI().getPath();
                    Object payload;
                    int status = 200;
                    if (path.equals("/api/tags")) {
                        status = tagsStatus;
                        observing.countDown();
                        releaseObservation.await(3, TimeUnit.SECONDS);
                        var models = new java.util.ArrayList<Map<String,String>>();
                        if (installed) models.add(Map.of("name",model,"digest",EmbeddingProviderFailoverTest.DIGEST));
                        if (secondaryModel!=null) models.add(Map.of("name",secondaryModel,"digest",EmbeddingProviderFailoverTest.DIGEST));
                        payload = Map.of("models",models);
                    }
                    else if (path.equals("/api/ps")) {
                        status = psStatus;
                        payload = psBody == null ? Map.of("models", loaded ? List.of(Map.of("name", residentModel==null?model:residentModel, "size", 5295200000L, "size_vram", 4294967296L)) : List.of()) : mapper.readTree(psBody);
                    } else if (path.equals("/api/chat") || path.equals("/api/embed")) {
                        String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                        posts.add(body);
                        var request=mapper.readTree(body);
                        String requested=request.path("model").asText();
                        entered.countDown();
                        release.await(4, TimeUnit.SECONDS);
                        if (loaded && !requested.equals(residentModel==null?model:residentModel)) evictions.incrementAndGet();
                        residentModel=requested; loaded = true;
                        payload = path.equals("/api/chat") ? Map.of("model", requested, "done", true) : Map.of("model", requested, "embeddings",
                                request.path("input").isEmpty()?List.of():List.of(java.util.Collections.nCopies(1024,1.0)));
                    } else throw new AssertionError("Unexpected model operation " + path);
                    byte[] bytes = mapper.writeValueAsBytes(payload);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                finally { operations.decrementAndGet(); exchange.close(); }
            });
            server.start();
        }
        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); executor.shutdownNow(); }
    }
}
