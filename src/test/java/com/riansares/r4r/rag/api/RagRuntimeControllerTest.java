package com.riansares.r4r.rag.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.config.RagRetrievalProperties;
import com.riansares.r4r.rag.GenerationRouter;
import com.riansares.r4r.rag.OllamaModelPreloader;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagRuntimeControllerTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "jdbc:postgresql://db/rag?password=synthetic-private/path&user=test",
            "jdbc:postgresql://test:synthetic-private@db/rag#synthetic-private/path",
            "jdbc:postgresql://db/rag?password=synthetic-private#fragment" })
    void databaseMetadataNeverIncludesUrlParametersOrCredentials(String jdbcUrl) throws Exception {
        var info = new RagRuntimeController(new RagRetrievalProperties(3, .63), "nomic", "768",
                jdbcUrl, preloader, router).runtime();
        assertThat(info.database()).isEqualTo("rag");
        assertThat(new ObjectMapper().writeValueAsString(info)).doesNotContain("synthetic-private", "password=", "user=test");
    }

    @Test void authorityWithoutDatabaseDoesNotExposeCredentials() {
        var info = new RagRuntimeController(new RagRetrievalProperties(3, .63), "nomic", "768",
                "jdbc:postgresql://test:synthetic-private@db?password=synthetic-private", preloader, router).runtime();
        assertThat(info.database()).isEqualTo("PostgreSQL · pgvector");
    }

    @org.junit.jupiter.api.BeforeEach void defaults() {
        when(preloader.modelStates()).thenReturn(java.util.List.of());
        when(preloader.memoryFor(any())).thenReturn(new OllamaModelPreloader.MemoryState(false, java.util.List.of()));
    }
    private final AtomicBoolean pcUp = new AtomicBoolean(false), laptopUp = new AtomicBoolean(true);
    private final GenerationRouter router = new GenerationRouter(
            new GenerationRouter.Target(new GenerationRouter.Provider("PC", "http://192.168.1.207:11434", "qwen3:8b"), mock(ChatModel.class), pcUp::get),
            new GenerationRouter.Target(new GenerationRouter.Provider("ASUS", "http://192.168.1.209:11434", "qwen2.5:7b"), mock(ChatModel.class), laptopUp::get));
    private final OllamaModelPreloader preloader = mock(OllamaModelPreloader.class);
    private final RagRuntimeController controller = new RagRuntimeController(new RagRetrievalProperties(3, .63),
            "nomic-embed-text", "768", "", preloader, router);

    private void memory(String body) throws Exception {
        // Byte parsing is exercised against real simulated Ollama HTTP in OllamaModelPreloaderTest.
        var models = new java.util.ArrayList<OllamaModelPreloader.LoadedModel>();
        for (var model : new ObjectMapper().readTree(body).path("models")) {
            models.add(new OllamaModelPreloader.LoadedModel(model.path("name").asText(), "unknown",
                    bytes(model.get("size")), bytes(model.get("size_vram"))));
        }
        when(preloader.memoryFor(any())).thenReturn(new OllamaModelPreloader.MemoryState(true, models));
    }
    private Long bytes(com.fasterxml.jackson.databind.JsonNode value) {
        return value == null ? null : value.longValue();
    }

    @Test void queriesMemoryOnceOnActualFallbackWithoutProbingAvailabilityAgain() throws Exception {
        router.call(new Prompt("question"));
        pcUp.set(true); // A runtime read must not switch provider just because the PC recovered.
        memory("{\"models\":[{\"name\":\"qwen2.5:7b\",\"size\":100,\"size_vram\":50}]}");
        var info = controller.runtime();
        assertThat(info.preferredProvider().model()).isEqualTo("qwen3:8b");
        assertThat(info.configuredModel()).isEqualTo("qwen3:8b");
        assertThat(info.generationModel()).isEqualTo("qwen2.5:7b");
        assertThat(info.lastUsedProvider().inferenceHost().hostname()).isEqualTo("PC-ASUS-ZORIN");
        assertThat(info.modelStatus()).isEqualTo("Modelo cargado");
        assertThat(info.loadedModels()).singleElement().satisfies(m -> assertThat(m.vramBytes()).isEqualTo(50));
        verify(preloader).memoryFor(argThat(p -> p.baseUrl().equals("http://192.168.1.209:11434")));
    }

    @Test void recoverySelectsPcForNextGeneration() throws Exception {
        router.call(new Prompt("fallback"));
        pcUp.set(true);
        router.call(new Prompt("recovered"));
        memory("{\"models\":[{\"name\":\"qwen3:8b\"}]}");
        var info = controller.runtime();
        assertThat(info.lastUsedProvider().inferenceHost().hostname()).isEqualTo("PC-GIGA-ZORUX");
        assertThat(info.generationModel()).isEqualTo("qwen3:8b");
    }

    @Test void unloadedModelDoesNotMeanProviderUnavailable() throws Exception {
        router.call(new Prompt("fallback"));
        memory("{\"models\":[]}");
        assertThat(controller.runtime().modelStatus()).isEqualTo("Modelo descargado de memoria");
    }

    @Test void providerUnavailableIsDifferentFromAnUnloadedModel() throws Exception {
        router.call(new Prompt("fallback"));
        when(preloader.memoryFor(any())).thenReturn(new OllamaModelPreloader.MemoryState(false, java.util.List.of()));
        var info = controller.runtime();
        assertThat(info.modelStatus()).isEqualTo("Ollama no disponible");
        assertThat(info.lastUsedProvider().model()).isEqualTo("qwen2.5:7b");
        assertThat(info.generationState()).isEqualTo("success");
    }

    @Test void failedAttemptDoesNotAttributeOldResponseToAnyProvider() {
        router.call(new Prompt("success"));
        laptopUp.set(false);
        assertThatThrownBy(() -> router.call(new Prompt("failure"))).isInstanceOf(RuntimeException.class);
        var info = controller.runtime();
        assertThat(info.generationState()).isEqualTo("failed");
        assertThat(info.lastUsedProvider()).isNull();
        assertThat(info.generationModel()).isNull();
        assertThat(info.inferenceHost()).isNull();
        verify(preloader, never()).memoryFor(any());
    }

    @Test void preservesOllamaByteValuesWithoutConvertingOrEstimating() throws Exception {
        router.call(new Prompt("fallback"));
        memory("{\"models\":[{\"name\":\"qwen2.5:7b\",\"size\":5295200000,\"size_vram\":4294967296}]}");
        var model = controller.runtime().loadedModels().getFirst();
        assertThat(model.sizeBytes()).isEqualTo(5295200000L);
        assertThat(model.vramBytes()).isEqualTo(4294967296L);
    }

    @Test void preservesMissingTelemetryAndObservedZero() throws Exception {
        router.call(new Prompt("fallback"));
        when(preloader.memoryFor(any())).thenReturn(new OllamaModelPreloader.MemoryState(true, java.util.List.of(
                new OllamaModelPreloader.LoadedModel("missing", "unknown", null, null),
                new OllamaModelPreloader.LoadedModel("invalid", "unknown", null, null),
                new OllamaModelPreloader.LoadedModel("zero", "unknown", 0L, 0L))));
        var models = controller.runtime().loadedModels();
        assertThat(models.get(0).sizeBytes()).isNull();
        assertThat(models.get(0).vramBytes()).isNull();
        assertThat(models.get(1).sizeBytes()).isNull();
        assertThat(models.get(1).vramBytes()).isNull();
        assertThat(models.get(2).sizeBytes()).isZero();
        assertThat(models.get(2).vramBytes()).isZero();
    }
    @Test void cloudNeverProbesOllamaOrReportsLocalHardware() {
        var remote = new GenerationRouter.Target(new GenerationRouter.Provider("OpenAI","https://api.openai.com/v1","gpt-6-luna"), mock(ChatModel.class),()->true);
        var cloudRouter=new GenerationRouter(java.util.List.of(remote));
        cloudRouter.call(new Prompt("question"));
        var cloud=new RagRuntimeController(new RagRetrievalProperties(3,.63),"nomic-embed-text", "768","jdbc:postgresql://db/rag",preloader,cloudRouter);
        org.springframework.test.util.ReflectionTestUtils.setField(cloud, "remoteEnabled", "primary");
        var info=cloud.runtime();
        assertThat(info.remoteFallback().enabled()).isTrue();
        assertThat(info.lastUsedProvider().name()).isEqualTo("OpenAI");
        assertThat(info.lastUsedProvider().inferenceHost().type()).isEqualTo("cloud");
        assertThat(info.inferenceHost().cpu()).isNull();assertThat(info.loadedModels()).isEmpty();
        verify(preloader, never()).memoryFor(any());
    }

    @Test void selectedProviderStatusIsNotInferredFromFirstMatchingModelName() throws Exception {
        var sameRouter = new GenerationRouter(
                new GenerationRouter.Target(new GenerationRouter.Provider("PC", "http://192.168.1.207:11434", "shared:latest"), mock(ChatModel.class), () -> false),
                new GenerationRouter.Target(new GenerationRouter.Provider("ASUS", "http://192.168.1.209:11434", "shared:latest"), mock(ChatModel.class), () -> true));
        sameRouter.call(new Prompt("fallback"));
        memory("{\"models\":[]}");
        var pcStatus = new OllamaModelPreloader.ModelStatus("desktop", "shared:latest", "loaded", null, null, false);
        var laptopStatus = new OllamaModelPreloader.ModelStatus("laptop", "shared:latest", "unreachable", null, null, false);
        when(preloader.modelStates()).thenReturn(java.util.List.of(pcStatus, laptopStatus));
        when(preloader.statusFor(argThat(p -> p.name().equals("ASUS")))).thenReturn(laptopStatus);
        var sameController = new RagRuntimeController(new RagRetrievalProperties(3, .63), "nomic-embed-text", "768", "", preloader, sameRouter);
        assertThat(sameController.runtime().modelStatus()).isEqualTo("Ollama no disponible");
        verify(preloader).statusFor(argThat(p -> p.name().equals("ASUS")));
    }

    @Test void warmupIsAsyncAcceptedAndContainsNoPrivateAddresses() throws Exception {
        when(preloader.requestWarmup()).thenReturn(new OllamaModelPreloader.WarmupResult(
                java.util.List.of("desktop", "laptop", "embeddings"), java.util.List.of(), 0));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/rag/warmup"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.accepted.length()").value(3))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("192.168"))));
        when(preloader.requestWarmup()).thenReturn(new OllamaModelPreloader.WarmupResult(java.util.List.of(), java.util.List.of("desktop"), 60));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/rag/warmup"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "60"));
    }
}
