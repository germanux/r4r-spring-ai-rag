package com.riansares.r4r.llm_tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.riansares.r4r.evaluation.EvaluationQuestions;
import com.riansares.r4r.evaluation.EvaluationQuestions.QuestionCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional smoke test against a dedicated, running R4R test API.
 * Run: RAG_TEST_BASE_URL=http://127.0.0.1:18080 mvn verify -Pllm-it -Dit.test=RagLiveQuestionsIT
 * The 35 calls run sequentially; a slow local model may take several minutes.
 */
@Tag("live-rag")
@org.junit.jupiter.api.Tag("llm")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "r4r.test.llm.enabled", matches = "true",
        disabledReason = "NOT RUN: real LLM/E2E requires -Pllm-it and explicit test endpoints/credentials")
class RagLiveQuestionsIT {

    private static final String BASE_URL = System.getenv().getOrDefault(
            "RAG_TEST_BASE_URL", "").replaceAll("/+$", "");
    private static final URI ENDPOINT = URI.create(BASE_URL + "/api/rag/answers");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(180);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Pattern ONLY_CITATIONS =
            Pattern.compile("^(?:\\[S\\d+\\][\\s,;:.]*)+$");
    private static final Pattern CITATION_REFERENCE = Pattern.compile("\\[S\\d+\\]");

    public static final long SLEEP_WAIT = 2_000;
    private static final List<QuestionCase> CASES = EvaluationQuestions.cases("es");

    @org.junit.jupiter.api.BeforeAll
    static void requireDedicatedTestEndpoint() throws java.io.IOException {
        assertThat(BASE_URL.isBlank()).as("RAG_TEST_BASE_URL must explicitly select a dedicated test API").isFalse();
        RagAuditEvaluation.prepare();
    }

    @TestFactory
    Stream<DynamicTest> thirtyFiveRagQuestions() {
        assertThat(CASES).hasSize(35);

        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < CASES.size(); i++) {
            final int number = i + 1;
            final QuestionCase testCase = CASES.get(i);
            String selected = System.getProperty("r4r.test.case-ids", "");
            if (!selected.isBlank() && !java.util.Arrays.asList(selected.split(",")).contains(testCase.caseId())) continue;
            tests.add(DynamicTest.dynamicTest(
                    "%02d - %s".formatted(number, testCase.name()),
                    () -> verifyAnswer(number, testCase)));
        }
        return tests.stream();
    }

    // Separate opt-in regression; not one of the 35 main cases.
    @org.junit.jupiter.api.Test
    @EnabledIfEnvironmentVariable(named = "RAG_TIMELINE_REGRESSION", matches = "(?i)true")
    void originalTimelineReasonRegression() throws Exception {
        verifyAnswer(36, EvaluationQuestions.timeline("es"), true);
    }

    private static void verifyAnswer(int number, QuestionCase testCase) throws Exception {
        verifyAnswer(number, testCase, false);
    }

    private static void verifyAnswer(int number, QuestionCase testCase, boolean allowAbstention) throws Exception {
        if (number > 1) {
            Thread.sleep(SLEEP_WAIT);
        }
        String requestBody = JSON.writeValueAsString(new QuestionRequest(testCase.question(), "es"));
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Origin", "https://athagon.tech")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        long started = System.nanoTime();
        HttpResponse<String> httpResponse = RagTestHttpClient.send(HTTP, request);
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;

        System.out.printf("%n[%02d/35] %s%nPregunta: %s%nHTTP: %d | Tiempo: %.2f s%n",
                number, testCase.name(), testCase.question(), httpResponse.statusCode(), seconds);
        System.out.println("JSON: " + RagAuditEvaluation.safeResponse(httpResponse.body()));

        boolean passed = false;
        try {
        assertThat(httpResponse.statusCode())
                .as("HTTP de %s: %s", testCase.name(), httpResponse.body())
                .isEqualTo(200);

        JsonNode result = JSON.readTree(httpResponse.body());
        assertThat(result.path("abstained").isBoolean()).isTrue();
        assertThat(result.path("answer").isTextual()).isTrue();
        assertThat(result.path("citations").isArray()).isTrue();

        String answer = result.path("answer").asText();
        boolean abstained = result.path("abstained").asBoolean();
        JsonNode citations = result.path("citations");

        var status = com.riansares.r4r.rag.AnswerStatus.valueOf(result.path("status").asText());
        assertThat(abstained).isEqualTo(status == com.riansares.r4r.rag.AnswerStatus.ABSTAINED);
        assertThat((allowAbstention && abstained) || testCase.acceptsStatus(status))
                .as("Unexpected status for %s: %s", testCase.caseId(), status).isTrue();
        if (abstained) {
            assertThat(abstained).as("Debe abstenerse fuera del dominio R4R").isTrue();
            assertThat(answer).isBlank();
            assertThat(citations.size()).isZero();
            passed = true;
            return;
        }

        assertThat(abstained).as("La pregunta es del dominio R4R").isFalse();
        assertThat(answer).isNotBlank();
        assertThat(ONLY_CITATIONS.matcher(answer.trim()).matches())
                .as("La respuesta no puede consistir exclusivamente en citas: %s", answer)
                .isFalse();
        assertThat(citations.size()).as("Deben llegar metadatos de fuentes").isGreaterThan(0);

        Set<String> labels = new TreeSet<>();
        for (JsonNode citation : citations) {
            String label = citation.path("label").asText();
            assertThat(label).matches("\\[S\\d+\\]");
            assertThat(citation.path("source").asText()).isNotBlank();
            labels.add(label);
        }

        Matcher matcher = CITATION_REFERENCE.matcher(answer);
        Set<String> mentioned = new TreeSet<>();
        while (matcher.find()) {
            mentioned.add(matcher.group());
        }
        assertThat(mentioned).as("La respuesta debe citar la evidencia").isNotEmpty();
        assertThat(labels).containsAll(mentioned);
        passed = true;
        } finally {
            RagAuditEvaluation.record(HTTP, BASE_URL, "es", number, testCase, httpResponse, seconds * 1000, passed);
        }
    }

    @org.junit.jupiter.api.AfterAll
    static void summarizeRagAndAudit() { RagAuditEvaluation.summary(); }

    private record QuestionRequest(String question, String responseLanguage) { }
}
