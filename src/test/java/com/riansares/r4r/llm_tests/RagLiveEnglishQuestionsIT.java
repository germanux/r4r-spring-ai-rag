package com.riansares.r4r.llm_tests;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Smoke test against the REAL, running R4R API (not mocks).
 *
 * Manual English smoke test; never enabled without RAG_LIVE_EN_TEST=true.
 * The live requests are disabled by default to keep regular builds offline.
 *
 * Run from the project root:
 *   RAG_LIVE_EN_TEST=true mvn -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false \
 *     -Dit.test=RagLiveEnglishQuestionsIT verify
 *
 * If the repository has no Maven wrapper, replace ./mvnw with mvn.
 * The 15 calls run sequentially; a slow local model may take several minutes.
 */
@Tag("live-rag")
@EnabledIfEnvironmentVariable(named = "RAG_LIVE_EN_TEST", matches = "(?i)true")
class RagLiveEnglishQuestionsIT {

    private static final String BASE_URL = System.getenv().getOrDefault(
            "RAG_TEST_BASE_URL", "http://192.168.1.203:18080").replaceAll("/+$", "");
    private static final URI ENDPOINT = URI.create(BASE_URL + "/api/rag/answers");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(180);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Pattern ONLY_CITATIONS =
            Pattern.compile("^(?:\\[S\\d+\\][\\s,;:.]*)+$");
    private static final Pattern CITATION_REFERENCE = Pattern.compile("\\[S\\d+\\]");

    // 12 preguntas del dominio R4R y 3 preguntas ajenas para verificar abstención.
    private static final List<QuestionCase> CASES = List.of(
            new QuestionCase("Initial guidance", "Which details should I provide to get preliminary advice about my renovation?", "Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?", true),
            new QuestionCase("Concrete slab versus flooring", "In construction, how does the concrete base differ from the finished flooring?", "En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?", true),
            new QuestionCase("Access and cost", "Why does access affect the price of a concrete slab?", "¿Por qué el acceso cambia el precio de una solera?", true),
            new QuestionCase("Old house", "What is the approximate cost of a complete renovation of an old house?", "¿Cuánto orienta una reforma integral de una casa antigua?", true),
            new QuestionCase("Estimate", "What information should a customer provide to request an estimate?", "¿Qué datos debe aportar el cliente para solicitar un presupuesto?", true),
            new QuestionCase("Site visit", "Does initial cost guidance replace a technical site visit?", "¿Una primera orientación económica sustituye una visita técnica?", true),
            new QuestionCase("Patio", "What should be assessed before renovating a patio?", "¿Qué hay que estudiar antes de reformar un patio?", true),
            new QuestionCase("Ready-mix concrete", "When is it advisable to use ready-mix concrete for a slab?", "¿Cuándo conviene usar hormigón de camión en una solera?", true),
            new QuestionCase("Narrow corridor", "How does a narrow corridor affect the construction of a concrete slab?", "¿Cómo afecta un pasillo estrecho a la ejecución de una solera?", true),
            new QuestionCase("Damp", "What causes of damp can occur in an old home?", "¿Qué causas de humedad pueden aparecer en una vivienda antigua?", true),
            new QuestionCase("Bathroom", "What information is needed to estimate a bathroom renovation?", "¿Qué datos hacen falta para orientar una reforma de baño?", true),
            new QuestionCase("Timelines", "Why is it important to know the expected timeline before preparing a renovation estimate?", "¿Por qué es importante conocer el plazo previsto antes de presupuestar una reforma?", true),
            new QuestionCase("Out of domain: photosynthesis", "How do plants make their food through photosynthesis?", "¿De qué manera producen alimento las plantas mediante la fotosíntesis?", false),
            new QuestionCase("Out of domain: astronomy", "What is the mass of Jupiter in kilograms?", "¿Cuál es la masa de Júpiter en kilogramos?", false),
            new QuestionCase("Out of domain: literature", "Who wrote the novel One Hundred Years of Solitude?", "¿Quién escribió la novela Cien años de soledad?", false)
    );

    @TestFactory
    Stream<DynamicTest> fifteenRagQuestions() {
        assertThat(CASES).hasSize(15);

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

    private static void verifyAnswer(int number, QuestionCase testCase) throws Exception {
        if (number > 1) {
            Thread.sleep(SLEEP_WAIT);
        }
        String requestBody = JSON.writeValueAsString(new QuestionRequest(testCase.question(), testCase.retrievalQuestion(), "en"));
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

        System.out.printf("%n[%02d/15] %s%nPregunta: %s%nHTTP: %d | Tiempo: %.2f s%n",
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

        if (!testCase.expectsEvidence()) {
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

    private record QuestionCase(String name, String question, String retrievalQuestion, boolean expectsEvidence) { }
    private record QuestionRequest(String question, String retrievalQuestion, String responseLanguage) { }
}
