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

import static com.riansares.r4r.llm_tests.RagLiveQuestionsIT.SLEEP_WAIT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional smoke test against a dedicated, running R4R test API.
 * Run: RAG_TEST_BASE_URL=http://127.0.0.1:18080 mvn verify -Pllm-it -Dit.test=RagLiveEnglishQuestionsIT
 * The 35 calls run sequentially; a slow local model may take several minutes.
 */
@Tag("live-rag")
@org.junit.jupiter.api.Tag("llm")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "r4r.test.llm.enabled", matches = "true",
        disabledReason = "NOT RUN: real LLM/E2E requires -Pllm-it and explicit test endpoints/credentials")
class RagLiveEnglishQuestionsIT {

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

    private static final List<QuestionCase> CASES = EvaluationQuestions.cases("en");

    @org.junit.jupiter.api.BeforeAll
    static void requireDedicatedTestEndpoint() {
        assertThat(BASE_URL.isBlank()).as("RAG_TEST_BASE_URL must explicitly select a dedicated test API").isFalse();
    }

    @TestFactory
    Stream<DynamicTest> thirtyFiveRagQuestions() {
        assertThat(CASES).hasSize(35);

        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < CASES.size(); i++) {
            final int number = i + 1;
            final QuestionCase testCase = CASES.get(i);
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
        verifyAnswer(36, EvaluationQuestions.timeline("en"), true);
    }

    private static void verifyAnswer(int number, QuestionCase testCase) throws Exception {
        verifyAnswer(number, testCase, false);
    }

    private static void verifyAnswer(int number, QuestionCase testCase, boolean allowAbstention) throws Exception {
        if (number > 1) {
            Thread.sleep(SLEEP_WAIT);
        }
        // Exercise backend-local normalization, not a pretranslated test query.
        String requestBody = JSON.writeValueAsString(new QuestionRequest(testCase.question(), null, "en"));
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Origin", "https://athagon.tech")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        long started = System.nanoTime();
        HttpResponse<String> httpResponse = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            httpResponse = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (httpResponse.statusCode() != 429) break;
            String retryAfter = httpResponse.headers().firstValue("Retry-After").orElse("20");
            long delay;
            try {
                delay = Long.parseLong(retryAfter);
            } catch (NumberFormatException ex) {
                delay = Math.max(0, Duration.between(java.time.Instant.now(),
                        java.time.ZonedDateTime.parse(retryAfter, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds() + 1);
            }
            Thread.sleep(Math.max(1, delay) * 1000);
        }
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;

        System.out.printf("%n[%02d/35] %s%nPregunta: %s%nHTTP: %d | Tiempo: %.2f s%n",
                number, testCase.name(), testCase.question(), httpResponse.statusCode(), seconds);
        System.out.println("JSON: " + httpResponse.body());

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

        if (!testCase.expectsEvidence() || (allowAbstention && abstained)) {
            assertThat(abstained).as("Debe abstenerse fuera del dominio R4R").isTrue();
            assertThat(answer).isBlank();
            assertThat(citations.size()).isZero();
            return;
        }

        assertThat(abstained).as("La pregunta es del dominio R4R").isFalse();
        assertThat(answer).isNotBlank();
        // A lightweight language check; citations and technical terms can remain Spanish.
        assertThat(answer).as("English answer").containsPattern("(?i)\\b(the|a|an|is|are|you|your|to|of|for|with|can|will|may|should|it|and|or|in|on|before|depending|please)\\b");
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
    }

    private record QuestionRequest(String question, String retrievalQuestion, String responseLanguage) { }
}
