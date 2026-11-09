package com.riansares.r4r.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class LocalRetrievalQueryTranslatorTest {
    @Test void translationReceivesInstructionsButNoCompanyEvidenceOrAnswerRequest() {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getContents()).contains("Do not add facts or answer it", "houses = viviendas", "offices = oficinas", "estimate = presupuesto", "concrete slab = solera", "damp or dampness = humedad", "old home = vivienda antigua", "What renovations?");
            return response(" ¿Qué reformas? ");
        });
        assertThat(new LocalRetrievalQueryTranslator(model).translate("What renovations?")).isEqualTo("¿Qué reformas?");
    }

    @Test void refusesEmptyOrAnswerLikeTranslationWithoutChangingTheQuerySilently() {
        for (String output : List.of("", " ", "__R4R_ABSTAIN__", "__r4r_abstain__",
                "An answer [S1]", "An answer [s1]", "<think>Answer first</think> ¿Qué reformas?",
                "x".repeat(2001))) {
            var model = mock(ChatModel.class);
            when(model.call(any(Prompt.class))).thenReturn(response(output));
            assertThatThrownBy(() -> new LocalRetrievalQueryTranslator(model).translate("Question"))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }
    @Test void refusesNullOrMissingGeneration() {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(null, new ChatResponse(List.of()));
        var translator = new LocalRetrievalQueryTranslator(model);
        assertThatThrownBy(() -> translator.translate("Question")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> translator.translate("Question")).isInstanceOf(ResponseStatusException.class);
    }

    @Test void propagatesProviderTimeoutWithoutClaimingNoEvidence() {
        var model = mock(ChatModel.class);
        var timeout = new org.springframework.web.client.ResourceAccessException("read timeout",
                new java.net.SocketTimeoutException("timed out"));
        when(model.call(any(Prompt.class))).thenThrow(timeout);
        assertThatThrownBy(() -> new LocalRetrievalQueryTranslator(model).translate("Question"))
                .isSameAs(timeout);
        verify(model, times(1)).call(any(Prompt.class));
    }

    @Test void translationUsesExistingLaptopFallbackWhenPreferredProviderTimesOut() {
        var pc = mock(ChatModel.class);
        var laptop = mock(ChatModel.class);
        when(pc.call(any(Prompt.class))).thenThrow(new org.springframework.web.client.ResourceAccessException(
                "read timeout", new java.net.SocketTimeoutException("timed out")));
        when(laptop.call(any(Prompt.class))).thenReturn(response("¿Qué reformas?"));
        var router = new GenerationRouter(
                new GenerationRouter.Target(new GenerationRouter.Provider("PC", "http://pc", "preferred"), pc, () -> true),
                new GenerationRouter.Target(new GenerationRouter.Provider("Laptop", "http://laptop", "fallback"), laptop, () -> true));
        assertThat(new LocalRetrievalQueryTranslator(router).translate("What renovations?"))
                .isEqualTo("¿Qué reformas?");
        verify(pc, times(1)).call(any(Prompt.class));
        verify(laptop, times(1)).call(any(Prompt.class));
    }

    private static ChatResponse response(String text) { return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }
}
