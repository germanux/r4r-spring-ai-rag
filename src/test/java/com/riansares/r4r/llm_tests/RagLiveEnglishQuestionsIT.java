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
 * Optional smoke test against a dedicated, running R4R test API.
 * Run: RAG_TEST_BASE_URL=http://127.0.0.1:18080 mvn verify -Pllm-it -Dit.test=RagLiveEnglishQuestionsIT
 * The 30 calls run sequentially; a slow local model may take several minutes.
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

    // 19 preguntas con apoyo total/parcial y 11 sin cobertura suficiente; ver generalization-30.json.
    private static final List<QuestionCase> CASES = List.of(
            new QuestionCase("Primera orientación", "If I want initial guidance on my renovation, what details should I provide?", "Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?", true),
            new QuestionCase("Solera frente a solado", "In construction, how does the concrete base differ from the finished flooring?", "En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?", true),
            new QuestionCase("Precio por acceso", "Why does access affect the price of a concrete slab?", "¿Por qué el acceso cambia el precio de una solera?", true),
            new QuestionCase("Casa antigua", "What is the approximate cost of a complete renovation of an old house?", "¿Cuánto orienta una reforma integral de una casa antigua?", true),
            new QuestionCase("Visita previa", "Does initial cost guidance replace a technical site visit?", "¿Una primera orientación económica sustituye una visita técnica?", true),
            new QuestionCase("Patio", "What should be checked before paving a patio?", "¿Qué debe revisarse antes de pavimentar un patio?", true),
            new QuestionCase("Hormigón de camión", "When is it advisable to use ready-mix concrete for a slab?", "¿Cuándo conviene usar hormigón de camión en una solera?", true),
            new QuestionCase("Pasillo estrecho", "How does a narrow corridor affect the construction of a concrete slab?", "¿Cómo afecta un pasillo estrecho a la ejecución de una solera?", true),
            new QuestionCase("Humedades", "What causes of damp can occur in an old home?", "¿Qué causas de humedad pueden aparecer en una vivienda antigua?", true),
            new QuestionCase("Plazos", "Should a renovation estimate include the execution timeline?", "¿Debe incluir el presupuesto de una reforma los plazos de ejecución?", true),
            new QuestionCase("Presupuesto", "What information should a customer provide to request an estimate?", "¿Qué datos debe aportar el cliente para solicitar un presupuesto?", true),
            new QuestionCase("Baño", "What information is needed to estimate a bathroom renovation?", "¿Qué datos hacen falta para orientar una reforma de baño?", true),
            new QuestionCase("Servicios de la empresa", "What kinds of renovation work does this company do? Does it work on houses and offices?", "¿Qué tipos de reformas realiza esta empresa? ¿Trabaja en viviendas y oficinas?", true),
            new QuestionCase("Cal aérea y cal hidráulica", "What are the differences between air lime and natural hydraulic lime when working on old walls?", "¿Qué diferencias hay entre la cal aérea y la cal hidráulica natural para trabajar sobre muros antiguos?", true),
            new QuestionCase("Baldosa sobre pavimento existente", "What precautions should be taken before laying new tiles over an existing floor?", "¿Qué precauciones hay que tomar antes de colocar baldosa nueva sobre un pavimento existente?", true),
            new QuestionCase("Independiente 1", "I'm thinking of buying a fairly old flat, but there are cracks in some walls. How can I tell whether they are superficial or whether I'm getting into a serious problem?", "Estoy pensando en comprar un piso bastante antiguo, pero tiene grietas en algunas paredes. ¿Cómo puedo saber si es algo superficial o si me voy a meter en un problema serio?", true),
            new QuestionCase("Independiente 2", "The kitchen cabinets are fine. How can I refresh it without taking everything apart?", "Los muebles de la cocina están bien. ¿Cómo la renuevo sin desmontarlo todo?", true),
            new QuestionCase("Independiente 4", "When it rains heavily, my terrace leaks into the flat below. Where do I start?", "Cuando llueve fuerte, mi terraza cala al piso de abajo. ¿Por dónde empiezo?", true),
            new QuestionCase("Independiente 7", "We've just bought a flat and want to renovate it before moving in. In what order should the work be done to avoid having to break things that are already finished?", "Acabamos de comprar un piso y queremos reformarlo antes de mudarnos. ¿En qué orden conviene hacer los trabajos para evitar tener que romper cosas que ya están terminadas?", true),
            new QuestionCase("Independiente 3", "In my home, we can hear absolutely everything the upstairs neighbours do. Can the sound insulation be improved without losing too much ceiling height in the rooms?", "En mi casa se oye absolutamente todo lo que hacen los vecinos de arriba. ¿Se puede mejorar el aislamiento acústico sin perder demasiada altura en las habitaciones?", false),
            new QuestionCase("Independiente 5", "I want to replace my home's windows, but I don't know whether triple glazing is really worthwhile or whether double glazing is enough.", "Quiero cambiar las ventanas de mi casa, pero no sé si realmente merece la pena instalar triple acristalamiento o si con doble es suficiente.", false),
            new QuestionCase("Independiente 6", "I have a long, dark corridor. How can I get more light and make better use of the space?", "Tengo un pasillo largo y oscuro. ¿Cómo gano luz y aprovecho el espacio?", false),
            new QuestionCase("Independiente 8", "The wooden beams in my old house are bowed. Can they be reinforced or do they need replacing?", "Las vigas de madera de mi casa vieja están combadas. ¿Se refuerzan o se cambian?", false),
            new QuestionCase("Independiente 9", "Our office is very hot in some areas and cold in others. Can this be solved by renovating the climate-control installation without replacing the whole system?", "Tenemos una oficina donde hace mucho calor en unas zonas y frío en otras. ¿Se puede solucionar con una reforma de la climatización sin cambiar todo el sistema?", false),
            new QuestionCase("Independiente 10", "I want to turn a shop into a café. What building work do I need before opening?", "Quiero pasar de tienda a cafetería. ¿Qué obras necesito para abrir?", false),
            new QuestionCase("Fuera de corpus: geotermia", "What capacity and how many geothermal collectors do I need to heat and cool a 180 m² home?", "¿Qué potencia y qué número de captadores geotérmicos necesito para climatizar una vivienda de 180 m²?", false),
            new QuestionCase("Fuera de corpus: cargador eléctrico", "What exact cable cross-section and circuit breaker does the REBT require for installing an 11 kW electric car charger in my garage?", "¿Qué sección exacta de cable y qué magnetotérmico exige el REBT para instalar un cargador de coche eléctrico de 11 kW en mi garaje?", false),
            new QuestionCase("Fuera de dominio: fotosíntesis", "How do plants make their food through photosynthesis?", "¿De qué manera producen alimento las plantas mediante la fotosíntesis?", false),
            new QuestionCase("Fuera de dominio: astronomía", "What is the mass of Jupiter in kilograms?", "¿Cuál es la masa de Júpiter en kilogramos?", false),
            new QuestionCase("Fuera de dominio: literatura", "Who wrote the novel One Hundred Years of Solitude?", "¿Quién escribió la novela Cien años de soledad?", false)
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
        verifyAnswer(31, new QuestionCase("Regresión: motivo de los plazos no documentado", "Why is it important to know the expected timeline before preparing a renovation estimate?", "¿Por qué es importante conocer el plazo previsto antes de presupuestar una reforma?", true), true);
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
