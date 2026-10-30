package com.riansares.r4r.rag.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.config.RagRetrievalProperties;
import com.riansares.r4r.rag.GenerationRouter;
import com.riansares.r4r.rag.OllamaModelPreloader;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagRuntimeControllerTest {
    private final AtomicBoolean pcUp = new AtomicBoolean(false), laptopUp = new AtomicBoolean(true);
    private final GenerationRouter router = new GenerationRouter(
            new GenerationRouter.Target(new GenerationRouter.Provider("PC", "http://192.168.1.207:11434", "qwen3:8b"), mock(ChatModel.class), pcUp::get),
            new GenerationRouter.Target(new GenerationRouter.Provider("ASUS", "http://192.168.1.209:11434", "qwen2.5:7b"), mock(ChatModel.class), laptopUp::get));
    private final HttpClient http = mock(HttpClient.class);
    private final RagRuntimeController controller = new RagRuntimeController(new RagRetrievalProperties(3, .63),
            "qwen2.5:7b", "nomic-embed-text", "768", "", "http://192.168.1.209:11434",
            new ObjectMapper(), mock(OllamaModelPreloader.class), router, http);

    @SuppressWarnings("unchecked")
    private void memory(String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
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
        verify(http).send(argThat(r -> r.uri().toString().equals("http://192.168.1.209:11434/api/ps")), any(HttpResponse.BodyHandler.class));
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
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new java.io.IOException("unreachable"));
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
        verifyNoInteractions(http);
    }
}
