package com.riansares.r4r.rag.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.rag.CitedRagService;
import com.riansares.r4r.rag.GenerationRouter;
import com.riansares.r4r.rag.RagProgress;
import com.riansares.r4r.rag.RagResult;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Identical HTTP limits for REST/SSE; no configuration imports or external services. */
class RagRequestValidationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CitedRagService service = mock(CitedRagService.class);
    private final GenerationRouter router = mock(GenerationRouter.class);

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "{invalid json}"})
    void missingOrMalformedBodyDoesNotInvokeProviders(String body) throws Exception {
        try (var streams = new RagStreamController(service, router, Duration.ofSeconds(3))) {
            var mvc = MockMvcBuilders.standaloneSetup(new RagQueryController(service), streams).build();
            for (String endpoint : new String[]{"/api/rag/answers", "/api/rag/answers/stream"}) {
                mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest());
            }
            assertThat(streams.activeSessions()).isZero();
            verifyNoInteractions(service, router);
        }
    }

    static Stream<RagQueryRequest> invalidRequests() {
        return Stream.of(null, new RagQueryRequest(null), new RagQueryRequest(""), new RagQueryRequest(" \n\t"),
                new RagQueryRequest("q".repeat(701)), new RagQueryRequest("😀".repeat(350) + "q"),
                new RagQueryRequest("q", "r".repeat(2001), "en"),
                new RagQueryRequest("q", "😀".repeat(1000) + "r", "es"),
                new RagQueryRequest("q", null, "fr"));
    }

    @ParameterizedTest @MethodSource("invalidRequests")
    void rejectsBeforeProvidersOrSseAdmission(RagQueryRequest input) throws Exception {
        var emitters = new AtomicInteger();
        try (var streams = new RagStreamController(service, router, Duration.ofSeconds(3)) {
            @Override protected SseEmitter createEmitter() {
                emitters.incrementAndGet(); return super.createEmitter();
            }
        }) {
            var mvc = MockMvcBuilders.standaloneSetup(new RagQueryController(service), streams).build();
            for (String endpoint : new String[]{"/api/rag/answers", "/api/rag/answers/stream"}) {
                mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(input))).andExpect(status().isBadRequest())
                        .andExpect(content().string(""));
            }
            assertThat(emitters.get()).isZero();
            assertThat(streams.activeSessions()).isZero();
            assertThat(streams.activeWorkers()).isZero();
            verifyNoInteractions(service, router);
        }
    }

    static Stream<RagQueryRequest> validRequests() {
        return Stream.of(new RagQueryRequest("q".repeat(700)), new RagQueryRequest("😀".repeat(350)),
                new RagQueryRequest("q", "r".repeat(2000), "en"),
                new RagQueryRequest("q", "😀".repeat(1000), "ES"),
                new RagQueryRequest("q", null, ""), new RagQueryRequest("q", "", "es"));
    }

    @ParameterizedTest @MethodSource("validRequests")
    void acceptsBoundariesWithoutTruncation(RagQueryRequest input) throws Exception {
        when(service.answer(anyString())).thenReturn(RagResult.abstain());
        when(service.answer(anyString(), nullable(String.class), nullable(String.class))).thenReturn(RagResult.abstain());
        when(service.answer(anyString(), nullable(String.class), nullable(String.class), any(RagProgress.class))).thenReturn(RagResult.abstain());
        try (var streams = new RagStreamController(service, router, Duration.ofSeconds(3))) {
            var mvc = MockMvcBuilders.standaloneSetup(new RagQueryController(service), streams).build();
            mvc.perform(post("/api/rag/answers").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(input))).andExpect(status().isOk());
            var response = mvc.perform(post("/api/rag/answers/stream").contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM).content(json.writeValueAsString(input)))
                    .andExpect(request().asyncStarted()).andReturn();
            response.getAsyncResult(3000);
            mvc.perform(asyncDispatch(response)).andExpect(status().isOk());
            verify(service).answer(eq(input.question()), eq(input.retrievalQuestion()), eq(input.responseLanguage()), any(RagProgress.class));
            if (input.retrievalQuestion() == null && input.responseLanguage() == null) verify(service).answer(input.question());
            else verify(service).answer(input.question(), input.retrievalQuestion(), input.responseLanguage());
        }
    }
}
