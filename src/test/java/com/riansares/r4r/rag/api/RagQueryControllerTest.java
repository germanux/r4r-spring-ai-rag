package com.riansares.r4r.rag.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.rag.CitedRagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagQueryController.class)
class RagQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CitedRagService citedRagService;

    @MockBean private com.riansares.r4r.rag.GenerationRouter router;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test void reportsEmbeddingHostSeparatelyAndPreservesItWhenAddingAnAuditId() throws Exception {
        var progress = org.mockito.Mockito.mock(com.riansares.r4r.rag.RagProgress.class);
        when(progress.usedEmbeddingProvider()).thenReturn(new com.riansares.r4r.rag.GenerationRouter.Provider(
                "laptop-embedding", "http://192.168.1.209:11434", "qwen3-embedding:0.6b"));
        var generation = new com.riansares.r4r.rag.GenerationRouter.Execution(
                new com.riansares.r4r.rag.GenerationRouter.Provider("desktop", "http://192.168.1.207:11434", "qwen3:8b"), false);
        var response = RagQueryController.response(com.riansares.r4r.rag.RagResult.ofAnswer("Answer [S1]",
                List.of(new com.riansares.r4r.rag.Citation("[S1]", "guide.md", List.of("Guide"), 0))), generation, progress)
                .withAuditId("audit-reference");
        var json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        org.assertj.core.api.Assertions.assertThat(json.path("embeddingProvider").path("inferenceHost").path("type").asText()).isEqualTo("laptop");
        org.assertj.core.api.Assertions.assertThat(json.path("embeddingProvider").path("model").asText()).isEqualTo("qwen3-embedding:0.6b");
        org.assertj.core.api.Assertions.assertThat(json.path("provider").path("inferenceHost").path("type").asText()).isEqualTo("desktop");
        org.assertj.core.api.Assertions.assertThat(json.path("auditId").asText()).isEqualTo("audit-reference");
        org.assertj.core.api.Assertions.assertThat(json.toString()).doesNotContain("192.168.1.", "http://");
    }

    @Test
    void returnsAnswerWithCitations() throws Exception {
        when(citedRagService.answer(anyString(), org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.any(com.riansares.r4r.rag.RagProgress.class))).thenReturn(
                new com.riansares.r4r.rag.RagResult(
                        "Generated answer",
                        false,
                        List.of(
                                new com.riansares.r4r.rag.Citation("[S1]", "doc1.md", List.of("Section"), 0),
                                new com.riansares.r4r.rag.Citation("[S2]", "doc2.md", List.of("Other"), 1)
                        )
                )
        );

        RagQueryRequest request = new RagQueryRequest("test question");

        mockMvc.perform(post("/api/rag/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", is("Generated answer")))
                .andExpect(jsonPath("$.abstained", is(false)))
                .andExpect(jsonPath("$.status", is("ANSWERED")))
                .andExpect(jsonPath("$.chunks").doesNotExist())
                .andExpect(jsonPath("$.auditId").doesNotExist())
                .andExpect(jsonPath("$.generatedOutput").doesNotExist())
                .andExpect(jsonPath("$.citations", hasSize(2)))
                .andExpect(jsonPath("$.citations[0].source", is("doc1.md")))
                .andExpect(jsonPath("$.citations[0].headingPath", hasSize(1)))
                .andExpect(jsonPath("$.citations[0].headingPath[0]", is("Section")))
                .andExpect(jsonPath("$.citations[0].ordinal", is(0)))
                .andExpect(jsonPath("$.citations[0].label", is("[S1]")))
                .andExpect(jsonPath("$.citations[1].source", is("doc2.md")))
                .andExpect(jsonPath("$.citations[1].headingPath[0]", is("Other")))
                .andExpect(jsonPath("$.citations[1].ordinal", is(1)))
                .andExpect(jsonPath("$.citations[1].label", is("[S2]")));
    }

    @Test
    void returnsAbstentionWhenServiceReturnsEmptyResult() throws Exception {
        when(citedRagService.answer(anyString(), org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.any(com.riansares.r4r.rag.RagProgress.class))).thenReturn(
                com.riansares.r4r.rag.RagResult.abstain()
        );

        RagQueryRequest request = new RagQueryRequest("test question");

        mockMvc.perform(post("/api/rag/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", is("")))
                .andExpect(jsonPath("$.abstained", is(true)))
                .andExpect(jsonPath("$.status", is("ABSTAINED")))
                .andExpect(jsonPath("$.citations", hasSize(0)));
    }

    @Test
    void acceptsEnglishRequestWithoutChangingCitationMetadata() throws Exception {
        when(citedRagService.answer(org.mockito.ArgumentMatchers.eq("How much?"), org.mockito.ArgumentMatchers.eq("¿Cuánto cuesta?"), org.mockito.ArgumentMatchers.eq("en"), org.mockito.ArgumentMatchers.any(com.riansares.r4r.rag.RagProgress.class)))
                .thenReturn(new com.riansares.r4r.rag.RagResult("It depends. [S1]", false,
                        List.of(new com.riansares.r4r.rag.Citation("[S1]", "baño.md", List.of("Reformas"), 1))));
        mockMvc.perform(post("/api/rag/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RagQueryRequest("How much?", "¿Cuánto cuesta?", "en"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", is("It depends. [S1]")))
                .andExpect(jsonPath("$.abstained", is(false)))
                .andExpect(jsonPath("$.status", is("ANSWERED")))
                .andExpect(jsonPath("$.citations[0].source", is("baño.md")))
                .andExpect(jsonPath("$.citations[0].headingPath[0]", is("Reformas")))
                .andExpect(jsonPath("$.citations[0].ordinal", is(1)));
    }

    @Test
    void returns400WhenQuestionIsNull() throws Exception {
        RagQueryRequest request = new RagQueryRequest(null);

        mockMvc.perform(post("/api/rag/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenQuestionIsBlank() throws Exception {
        RagQueryRequest request = new RagQueryRequest("   ");

        mockMvc.perform(post("/api/rag/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenRequestBodyIsAbsent() throws Exception {
        mockMvc.perform(post("/api/rag/answers")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenRequestBodyIsMalformedJson() throws Exception {
        String malformedJson = "{invalid json content}";
        mockMvc.perform(post("/api/rag/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson))
                .andExpect(status().isBadRequest());
    }
    @Test void returnsRequestProviderWithoutChangingAnswerAndCitations() throws Exception {
        when(citedRagService.answer(org.mockito.ArgumentMatchers.eq("question"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(com.riansares.r4r.rag.RagProgress.class))).thenReturn(com.riansares.r4r.rag.RagResult.ofAnswer("Answer [S1]", List.of(new com.riansares.r4r.rag.Citation("[S1]", "guide.md", List.of("Guide"), 0))));
        when(router.requestExecution()).thenReturn(new com.riansares.r4r.rag.GenerationRouter.Execution(
                new com.riansares.r4r.rag.GenerationRouter.Provider("OpenAI","https://api.openai.com/v1","gpt-6-luna"),false));
        mockMvc.perform(post("/api/rag/answers").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"question\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.provider.name").value("OpenAI"))
                .andExpect(jsonPath("$.provider.model").value("gpt-6-luna"))
                .andExpect(jsonPath("$.provider.inferenceHost.type").value("cloud"));
    }

}
