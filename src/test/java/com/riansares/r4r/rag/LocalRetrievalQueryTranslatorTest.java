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
            assertThat(prompt.getContents()).contains("Do not add facts or answer it", "houses = viviendas", "offices = oficinas", "What renovations?");
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
    private static ChatResponse response(String text) { return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }
}
