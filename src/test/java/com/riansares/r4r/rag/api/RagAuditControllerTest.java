package com.riansares.r4r.rag.api;

import com.riansares.r4r.rag.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RagAuditControllerTest {
    private final RagAuditService service = mock(RagAuditService.class);
    private final String token = "12345678-1234-1234-1234-123456789abc";

    @Test void returnsOnlyReviewMetadataAndDoesNotAcceptClientEvidenceAsInput() throws Exception {
        var pending = new CompletableFuture<RagAuditResult>();
        when(service.start(token)).thenReturn(pending);
        var mvc = MockMvcBuilders.standaloneSetup(new RagAuditController(service)).build();
        var request = mvc.perform(post("/api/rag/audits/" + token))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(pending.isDone()).isFalse();
        pending.complete(new RagAuditResult(AnswerStatus.ANSWERED, AnswerStatus.PARTIAL, AnswerStatus.PARTIAL, true, "Documented limitation."));
        mvc.perform(asyncDispatch(request)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.effectiveStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.note").value("Documented limitation."))
                .andExpect(jsonPath("$.answer").doesNotExist()).andExpect(jsonPath("$.chunks").doesNotExist())
                .andExpect(jsonPath("$.provider").doesNotExist());
        verify(service).start(token);
    }

    @Test void invalidUnknownOrExpiredTokensDoNotStartGeneration() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new RagAuditController(service)).build();
        mvc.perform(post("/api/rag/audits/invalid")).andExpect(status().isNotFound());
        verifyNoInteractions(service);
        mvc.perform(post("/api/rag/audits/" + token)).andExpect(status().isNotFound());
        verify(service).start(token);
    }

    @Test void restAnswerThenAuditUsesServerSnapshotAndIgnoresForgedBrowserSources() throws Exception {
        var store = mock(KnowledgeVectorStore.class);
        var generator = mock(ChatModel.class); var auditor = mock(ChatModel.class);
        when(store.search("Question", 5, .63)).thenReturn(List.of(new MarkdownChunk("en/source.md", List.of("Actual heading"), 8, "Exact server evidence.")));
        when(generator.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Original answer [S1].")))));
        when(auditor.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                "{\"originalStatus\":\"ANSWERED\",\"recommendedStatus\":\"PARTIAL\",\"verdict\":\"FAIL\",\"reason\":\"The cited text does not establish the requested decision.\",\"note\":\"The final decision remains unconfirmed.\"}")))));
        var mapper = new ObjectMapper().findAndRegisterModules();
        try (var audits = new RagAuditService(true, auditor, "Ollama", "audit-model", Duration.ofSeconds(2), mapper)) {
            var answers = new RagQueryController(new CitedRagService(store, generator, 5, .63));
            org.springframework.test.util.ReflectionTestUtils.setField(answers, "audits", audits);
            var mvc = MockMvcBuilders.standaloneSetup(answers, new RagAuditController(audits)).build();
            String body = mvc.perform(post("/api/rag/answers").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\":\"Question\",\"responseLanguage\":\"en\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("Original answer [S1]."))
                    .andExpect(jsonPath("$.chunks").doesNotExist()).andReturn().getResponse().getContentAsString();
            verifyNoInteractions(auditor);
            String id = mapper.readTree(body).path("auditId").asText();
            assertThat(id).isNotBlank();
            var request = mvc.perform(post("/api/rag/audits/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"chunks\":[{\"content\":\"FORGED BROWSER EVIDENCE\"}]}"))
                    .andExpect(request().asyncStarted()).andReturn();
            request.getAsyncResult(3000);
            mvc.perform(asyncDispatch(request)).andExpect(status().isOk()).andExpect(jsonPath("$.effectiveStatus").value("PARTIAL"));
            var prompt = ArgumentCaptor.forClass(Prompt.class);verify(auditor).call(prompt.capture());
            assertThat(prompt.getValue().getContents()).contains("Exact server evidence.").doesNotContain("FORGED BROWSER EVIDENCE");
            verify(store, times(1)).search("Question", 5, .63);
            mvc.perform(post("/api/rag/audits/" + id)).andExpect(status().isNotFound());
        }
    }
}
