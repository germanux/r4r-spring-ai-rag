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
        when(pc.call(prompt)).thenThrow(new IllegalStateException("connection lost"));
        router.call(prompt);
        verify(pc).call(prompt);
        verify(laptop).call(prompt);
        assertThat(router.lastUsed().model()).isEqualTo("fallback");
    }
    @Test void bothGenerationFailuresProduceControlled503() {
        when(pc.call(prompt)).thenThrow(new IllegalStateException("disconnected"));
        when(laptop.call(prompt)).thenThrow(new IllegalStateException("disconnected"));
        assertThatThrownBy(() -> router.call(prompt)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
    }
    @Test void bothUnavailableProduceControlled503() {
        pcUp.set(false); laptopUp.set(false);
        assertThatThrownBy(() -> router.call(prompt)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        verifyNoInteractions(pc, laptop);
    }
}
