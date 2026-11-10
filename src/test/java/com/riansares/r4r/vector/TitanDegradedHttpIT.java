package com.riansares.r4r.vector;

import com.riansares.r4r.rag.RagProgress;
import com.riansares.r4r.vector.titan.TitanResponses;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reuses the cold-PG HTTP/SSE fixture and original assertions; only Lambda transport is mocked. */
@TestPropertySource(properties = {"r4r.rag.vector.titan.enabled=true", "r4r.rag.vector.titan.minimum-similarity=0.6"})
@Tag("offline")
class TitanDegradedHttpIT extends VectorDegradedHttpIT {
    @MockBean(name = "titanLambdaClient") LambdaClient lambda;
    void nomicDown() { when(aws.queryVectors(any(QueryVectorsRequest.class))).thenThrow(SdkClientException.create("simulated Nomic unavailable")); }
    @Test void tertiaryCitedRestAndSseWorkWithoutPostgresAndNomic() throws Exception {
        nomicDown(); when(lambda.invoke(any(InvokeRequest.class))).thenReturn(TitanResponses.response(TitanResponses.body(true)));
        var rest = post("/api/rag/answers");
        assertThat(rest.statusCode()).isEqualTo(200); assertThat(rest.body()).contains("faq.md", "[S1]");
        var sse = post("/api/rag/answers/stream");
        assertThat(sse.body()).contains("event:ANSWER_READY", "faq.md", "\"vectorSearchMs\":2.0", "lambdaInvokeMs")
                .doesNotContain("event:ERROR", "simulated Nomic");
    }
    @Test void lambdaTimeoutReturnsControlled503AndSseError() throws Exception {
        nomicDown(); when(lambda.invoke(any(InvokeRequest.class))).thenThrow(ApiCallTimeoutException.builder().message("private-secret").build());
        var rest = post("/api/rag/answers"); assertThat(rest.statusCode()).isEqualTo(503);
        assertThat(rest.body()).doesNotContain("private-secret");
        var sse = post("/api/rag/answers/stream");
        assertThat(sse.body()).contains("event:ERROR").doesNotContain("event:ANSWER_READY", "private-secret");
        verify(router, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class), any(RagProgress.class));
    }
    static Stream<Arguments> canonicalCases() throws Exception {
        // The existing live-test questions remain the sole source. No new ES/EN question battery.
        List<Arguments> cases = new ArrayList<>();
        var pattern = Pattern.compile("new QuestionCase\\(\"([^\"\\n]*)\", \"([^\"\\n]*)\"(?:, \"([^\"\\n]*)\")?, (true|false)\\)");
        for (String lang : List.of("es", "en")) {
            var file = Path.of("src/test/java/com/riansares/r4r/llm_tests", lang.equals("es") ? "RagLiveQuestionsIT.java" : "RagLiveEnglishQuestionsIT.java");
            String source = Files.readString(file).split("CASES = List.of\\(", 2)[1].split("\\n    \\);", 2)[0];
            var matcher = pattern.matcher(source); int count = 0;
            while (matcher.find()) { cases.add(Arguments.of(lang, matcher.group(2), Boolean.parseBoolean(matcher.group(4)))); count++; }
            assertThat(count).isEqualTo(30);
        }
        return cases.stream();
    }
    @ParameterizedTest @MethodSource("canonicalCases")
    void canonicalBilingualContractWithSimulatedRetrieval(String lang, String question, boolean evidence) throws Exception {
        nomicDown(); when(lambda.invoke(any(InvokeRequest.class))).thenReturn(TitanResponses.response(TitanResponses.body(evidence)));
        String answer = lang.equals("en") ? "The company renovates homes and offices. [S1]" : "La empresa reforma viviendas y oficinas. [S1]";
        when(router.call(any(org.springframework.ai.chat.prompt.Prompt.class), any(RagProgress.class))).thenAnswer(invocation -> {
            RagProgress progress = invocation.getArgument(1); progress.usedProvider(provider);
            return new org.springframework.ai.chat.model.ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(
                    new org.springframework.ai.chat.messages.AssistantMessage(answer))));
        });
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/rag/answers"))
                .timeout(java.time.Duration.ofSeconds(5)).header("Origin", "http://127.0.0.1:8000")
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("question", question, "retrievalQuestion", question, "responseLanguage", lang)))).build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var body = json.readTree(response.body());
            assertThat(body.path("abstained").booleanValue()).isEqualTo(!evidence);
            if (evidence) {
                assertThat(body.at("/citations/0/source").asText()).isEqualTo("faq.md");
                assertThat(body.path("answer").asText()).contains("[S1]");
            } else {
                assertThat(body.path("citations")).isEmpty();
                verify(router, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class), any(RagProgress.class));
            }
        }
        // This validates contracts and fallback with mocks, not semantic relevance or real AWS latency.
    }
}
