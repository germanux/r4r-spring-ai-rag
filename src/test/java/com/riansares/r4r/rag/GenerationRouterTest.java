package com.riansares.r4r.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import java.util.List;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenerationRouterTest {
    private final ChatModel pc = mock(ChatModel.class), laptop = mock(ChatModel.class);
    private final AtomicBoolean pcUp = new AtomicBoolean(true), laptopUp = new AtomicBoolean(true);
    private final GenerationRouter router = new GenerationRouter(
            new GenerationRouter.Target(new GenerationRouter.Provider("PC", "http://pc", "preferred"), pc, pcUp::get),
            new GenerationRouter.Target(new GenerationRouter.Provider("Laptop", "http://laptop", "fallback"), laptop, laptopUp::get));
    @org.junit.jupiter.api.BeforeEach void responses() {
        var answer = new ChatResponse(List.of(new Generation(new AssistantMessage("Answer [S1]"))));
        when(pc.call(any(Prompt.class))).thenReturn(answer); when(laptop.call(any(Prompt.class))).thenReturn(answer);
    }
    private final Prompt prompt = new Prompt("Evidence and original question");

    @Test void prefersPcAndDoesNotFallbackForAValidResponse() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("__R4R_ABSTAIN__"))));
        when(pc.call(prompt)).thenReturn(response);
        assertThat(router.call(prompt)).isSameAs(response);
        assertThat(router.lastUsed().model()).isEqualTo("preferred");
        verifyNoInteractions(laptop);
    }
    @Test void usesLaptopWhenPcUnavailableAndReturnsToPcAfterRecovery() {
        pcUp.set(false);
        router.call(prompt);
        assertThat(router.lastUsed().model()).isEqualTo("fallback");
        pcUp.set(true);
        router.call(prompt);
        assertThat(router.lastUsed().model()).isEqualTo("preferred");
        verify(laptop).call(prompt);
        verify(pc).call(prompt);
    }
    @Test void reusesSamePromptIfGenerationFailsAfterProbe() {
        when(pc.call(prompt)).thenThrow(new org.springframework.web.client.ResourceAccessException("unavailable", new java.net.ConnectException()));
        router.call(prompt);
        verify(pc).call(prompt);
        verify(laptop).call(prompt);
        assertThat(router.lastUsed().model()).isEqualTo("fallback");
    }
    @Test void bothGenerationFailuresProduceControlled503() {
        when(pc.call(prompt)).thenThrow(new org.springframework.web.client.ResourceAccessException("unavailable", new java.net.ConnectException()));
        when(laptop.call(prompt)).thenThrow(new org.springframework.web.client.ResourceAccessException("unavailable", new java.net.ConnectException()));
        assertThatThrownBy(() -> router.call(prompt)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
    }
    @Test void bothUnavailableProduceControlled503() {
        pcUp.set(false); laptopUp.set(false);
        assertThatThrownBy(() -> router.call(prompt)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        verifyNoInteractions(pc, laptop);
    }
    @Test void remoteInTheMiddleIsUsedBeforeTheLastLocalAndCanFailOverToIt() {
        var remoteUp = new AtomicBoolean(true);
        var remote = mock(ChatModel.class);
        var response = new ChatResponse(List.of(new Generation(new AssistantMessage("remote"))));
        when(remote.call(prompt)).thenReturn(response);
        var ordered = new GenerationRouter(List.of(
                new GenerationRouter.Target(new GenerationRouter.Provider("PC", "http://pc", "primary"), pc, () -> false),
                new GenerationRouter.Target(new GenerationRouter.Provider("OpenAI", "https://api.openai.com/v1", "remote"), remote, remoteUp::get),
                new GenerationRouter.Target(new GenerationRouter.Provider("LP", "http://lp", "backup"), laptop, () -> true)));
        assertThat(ordered.call(prompt)).isSameAs(response);
        verifyNoInteractions(laptop);
        remoteUp.set(false);
        ordered.call(prompt);
        verify(laptop).call(prompt);
        assertThat(ordered.lastUsed().name()).isEqualTo("LP");
    }
}
