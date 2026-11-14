package com.riansares.r4r.llm_tests;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.rag.*;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Real Luna generation after simulated local outages, with deterministic evidence.
 * Retrieval is deliberately mocked: this test is independent of the embedding model
 * and dimensions. Real corpus retrieval belongs to the existing RagLive*QuestionsIT tests.
 */
@org.junit.jupiter.api.Tag("llm")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "r4r.test.llm.enabled", matches = "true",
        disabledReason = "NOT RUN: real LLM/E2E requires -Pllm-it and explicit test endpoints/credentials")
class LunaLiveIT {
    @Test
    void realRemoteAnswerPreservesEnglishAndCitationsAfterSimulatedLocalFailures() {
        String key = setting("OPENAI_API_KEY", "");
        assertThat(key).as("Existing API key available").isNotBlank();
        String remoteModel = setting("RAG_OPENAI_MODEL", "gpt-6-luna");
        var readTimeout = DurationStyle.detectAndParse(setting("RAG_OPENAI_READ_TIMEOUT", "35s"));
        int dailyLimit = Integer.parseInt(setting("RAG_OPENAI_MAX_CALLS_PER_DAY", "20"));
        var http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(Duration.ofSeconds(3));
        http.setReadTimeout(readTimeout);
        var model = new OpenAiResponsesModel(RestClient.builder().baseUrl("https://api.openai.com/v1")
                .requestFactory(http).build(), key, remoteModel, dailyLimit);
        var unavailable = mock(ChatModel.class);
        var primary = new GenerationRouter.Target(
                new GenerationRouter.Provider("local-primary", "http://127.0.0.1:1", "unused"), unavailable, () -> false);
        var secondary = new GenerationRouter.Target(
                new GenerationRouter.Provider("local-secondary", "http://127.0.0.1:2", "unused"), unavailable, () -> false);
        var remote = new GenerationRouter.Provider("OpenAI", "https://api.openai.com/v1", remoteModel);
        var router = new GenerationRouter(List.of(primary, secondary,
                new GenerationRouter.Target(remote, model, () -> true)));
        var store = mock(KnowledgeVectorStore.class);
        String question = "Does Riansares 4R renovate offices?";
        var evidence = new MarkdownChunk("services.md", List.of("Services"), 0,
                "Riansares 4R renovates houses and small offices.");
        when(store.search(question, 3, .63)).thenReturn(List.of(evidence));

        long started = System.nanoTime();
        var result = new CitedRagService(store, router, 3, .63).answer(question, null, "en");
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;

        assertThat(result.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(result.abstention()).isFalse();
        assertThat(result.answer()).containsIgnoringCase("office").contains("[S1]");
        assertThat(result.citations()).containsExactly(new Citation("[S1]", evidence.source(), evidence.headingPath(), evidence.index()));
        assertThat(router.lastUsed()).isEqualTo(remote);
        assertThat(router.requestExecution().failed()).isFalse();
        assertThat(model.lastUsage()).isNotNull();
        assertThat(model.lastUsage().inputTokens()).isPositive();
        assertThat(model.lastUsage().outputTokens()).isPositive();
        verify(store).search(question, 3, .63);
        verifyNoInteractions(unavailable);
        System.out.println("LUNA_LIVE provider=" + router.lastUsed().name() + " model=" + router.lastUsed().model()
                + " status=" + result.status() + " elapsedMs=" + elapsedMs
                + " answer=" + result.answer() + " usage=" + model.lastUsage());
    }

    private static String setting(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback).strip().replaceAll("^[\"']|[\"']$", "");
    }
}
