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

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void returnsAnswerWithCitations() throws Exception {
        when(citedRagService.answer(anyString())).thenReturn(
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
        when(citedRagService.answer(anyString())).thenReturn(
                com.riansares.r4r.rag.RagResult.abstain()
        );

        RagQueryRequest request = new RagQueryRequest("test question");

        mockMvc.perform(post("/api/rag/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", is("")))
                .andExpect(jsonPath("$.abstained", is(true)))
                .andExpect(jsonPath("$.citations", hasSize(0)));
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
}
