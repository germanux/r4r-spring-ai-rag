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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional smoke test against a dedicated, running R4R test API.
 * Run: RAG_TEST_BASE_URL=http://127.0.0.1:18080 mvn verify -Pllm-it -Dit.test=RagLiveQuestionsIT
 * The 30 calls run sequentially; a slow local model may take several minutes.
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

    public static final long SLEEP_WAIT = 21_000;
    // 19 preguntas con apoyo total/parcial y 11 sin cobertura suficiente; ver generalization-30.json.
    private static final List<QuestionCase> CASES = List.of(
            new QuestionCase("Primera orientación", "Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?", true),
            new QuestionCase("Solera frente a solado", "En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?", true),
            new QuestionCase("Precio por acceso", "¿Por qué el acceso cambia el precio de una solera?", true),
            new QuestionCase("Casa antigua", "¿Cuánto orienta una reforma integral de una casa antigua?", true),
            new QuestionCase("Visita previa", "¿Una primera orientación económica sustituye una visita técnica?", true),
            new QuestionCase("Patio", "¿Qué debe revisarse antes de pavimentar un patio?", true),
            new QuestionCase("Hormigón de camión", "¿Cuándo conviene usar hormigón de camión en una solera?", true),
            new QuestionCase("Pasillo estrecho", "¿Cómo afecta un pasillo estrecho a la ejecución de una solera?", true),
            new QuestionCase("Humedades", "¿Qué causas de humedad pueden aparecer en una vivienda antigua?", true),
            new QuestionCase("Plazos", "¿Debe incluir el presupuesto de una reforma los plazos de ejecución?", true),
            new QuestionCase("Presupuesto", "¿Qué datos debe aportar el cliente para solicitar un presupuesto?", true),
            new QuestionCase("Baño", "¿Qué datos hacen falta para orientar una reforma de baño?", true),
            new QuestionCase("Servicios de la empresa", "¿Qué tipos de reformas realiza esta empresa? ¿Trabaja en viviendas y oficinas?", true),
            new QuestionCase("Cal aérea y cal hidráulica", "¿Qué diferencias hay entre la cal aérea y la cal hidráulica natural para trabajar sobre muros antiguos?", true),
            new QuestionCase("Baldosa sobre pavimento existente", "¿Qué precauciones hay que tomar antes de colocar baldosa nueva sobre un pavimento existente?", true),
            new QuestionCase("Independiente 1", "Estoy pensando en comprar un piso bastante antiguo, pero tiene grietas en algunas paredes. ¿Cómo puedo saber si es algo superficial o si me voy a meter en un problema serio?", true),
            new QuestionCase("Independiente 2", "Los muebles de la cocina están bien. ¿Cómo la renuevo sin desmontarlo todo?", true),
            new QuestionCase("Independiente 4", "Cuando llueve fuerte, mi terraza cala al piso de abajo. ¿Por dónde empiezo?", true),
            new QuestionCase("Independiente 7", "Acabamos de comprar un piso y queremos reformarlo antes de mudarnos. ¿En qué orden conviene hacer los trabajos para evitar tener que romper cosas que ya están terminadas?", true),
            new QuestionCase("Independiente 3", "En mi casa se oye absolutamente todo lo que hacen los vecinos de arriba. ¿Se puede mejorar el aislamiento acústico sin perder demasiada altura en las habitaciones?", false),
            new QuestionCase("Independiente 5", "Quiero cambiar las ventanas de mi casa, pero no sé si realmente merece la pena instalar triple acristalamiento o si con doble es suficiente.", false),
            new QuestionCase("Independiente 6", "Tengo un pasillo largo y oscuro. ¿Cómo gano luz y aprovecho el espacio?", false),
            new QuestionCase("Independiente 8", "Las vigas de madera de mi casa vieja están combadas. ¿Se refuerzan o se cambian?", false),
            new QuestionCase("Independiente 9", "Tenemos una oficina donde hace mucho calor en unas zonas y frío en otras. ¿Se puede solucionar con una reforma de la climatización sin cambiar todo el sistema?", false),
            new QuestionCase("Independiente 10", "Quiero pasar de tienda a cafetería. ¿Qué obras necesito para abrir?", false),
            new QuestionCase("Fuera de corpus: geotermia", "¿Qué potencia y qué número de captadores geotérmicos necesito para climatizar una vivienda de 180 m²?", false),
            new QuestionCase("Fuera de corpus: cargador eléctrico", "¿Qué sección exacta de cable y qué magnetotérmico exige el REBT para instalar un cargador de coche eléctrico de 11 kW en mi garaje?", false),
            new QuestionCase("Fuera de dominio: fotosíntesis", "¿De qué manera producen alimento las plantas mediante la fotosíntesis?", false),
            new QuestionCase("Fuera de dominio: astronomía", "¿Cuál es la masa de Júpiter en kilogramos?", false),
            new QuestionCase("Fuera de dominio: literatura", "¿Quién escribió la novela Cien años de soledad?", false)
    );

    @org.junit.jupiter.api.BeforeAll
    static void requireDedicatedTestEndpoint() {
        assertThat(BASE_URL.isBlank()).as("RAG_TEST_BASE_URL must explicitly select a dedicated test API").isFalse();
    }

    @TestFactory
    Stream<DynamicTest> thirtyRagQuestions() {
        assertThat(CASES).hasSize(30);

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

    // Separate opt-in regression; not one of the 30 main cases.
    @org.junit.jupiter.api.Test
    @EnabledIfEnvironmentVariable(named = "RAG_TIMELINE_REGRESSION", matches = "(?i)true")
    void originalTimelineReasonRegression() throws Exception {
        verifyAnswer(31, new QuestionCase("Regresión: motivo de los plazos no documentado", "¿Por qué es importante conocer el plazo previsto antes de presupuestar una reforma?", true), true);
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
        HttpResponse<String> httpResponse = HTTP.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;

        System.out.printf("%n[%02d/30] %s%nPregunta: %s%nHTTP: %d | Tiempo: %.2f s%n",
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

    private record QuestionCase(String name, String question, boolean expectsEvidence) { }
    private record QuestionRequest(String question, String responseLanguage) { }
}
