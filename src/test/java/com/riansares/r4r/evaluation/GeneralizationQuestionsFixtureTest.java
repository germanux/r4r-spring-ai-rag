package com.riansares.r4r.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralizationQuestionsFixtureTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode fixture(String name) throws Exception {
        try (var input = GeneralizationQuestionsFixtureTest.class.getResourceAsStream("/evaluation/" + name)) {
            return JSON.readTree(input);
        }
    }

    @Test void reorderingPreservesQuestionIdentityAndExplicitWordingHistory() throws Exception {
        var initial = fixture("generalization-30-initial.json");
        var ordered = fixture("generalization-30.json");
        var identities = new HashSet<String>();
        assertThat(ordered.size()).isEqualTo(30);
        for (int i = 0; i < ordered.size(); i++) {
            var row = ordered.get(i);
            var original = initial.get(row.path("initialNumber").asInt() - 1);
            assertThat(row.path("number").asInt()).isEqualTo(i + 1);
            assertThat(identities.add(row.path("caseId").asText())).isTrue();
            for (var field : new String[]{"caseId", "es", "en", "origin"}) {
                var historical = field.equals("es") ? row.path("previousEs") : field.equals("en") ? row.path("previousEn") : null;
                assertThat(historical != null && !historical.isMissingNode() ? historical : row.get(field))
                        .as(field).isEqualTo(original.get(field));
            }
            assertThat(row.path("evidence").asBoolean()).isEqualTo(i < 19);
            assertThat(row.path("coverage").asText()).isIn("SUPPORTED", "PARTIAL", "INSUFFICIENT");
            assertThat(row.path("coverageRationale").asText()).isNotBlank();
        }
        assertThat(ordered.get(0).path("en").asText())
                .isEqualTo("If I want initial guidance on my renovation, what details should I provide?");
        assertThat(ordered.get(12).path("en").asText())
                .isEqualTo("What kinds of renovation work does this company do? Does it work on houses and offices?");
    }

    @Test void currentIndependentQuestionsAndBilingualConsumersStayAligned() throws Exception {
        var ordered = fixture("generalization-30.json");
        var independent = fixture("independent-questions-es.json");
        var originals = fixture("independent-questions-es-original.json");
        assertThat(independent.size()).isEqualTo(originals.size()).isEqualTo(20);
        for (int i = 0; i < independent.size(); i++) {
            var current = independent.get(i).path("question").asText();
            var previous = originals.get(i).path("question").asText();
            if ((i + 1) % 2 == 0) {
                assertThat(current.length()).isLessThan(previous.length());
            } else {
                assertThat(current).isEqualTo(previous);
            }
        }
        int independentCount = 0;
        for (var row : ordered) {
            if (row.has("independentId")) {
                assertThat(row.path("es").asText()).isEqualTo(independent.get(row.path("independentId").asInt() - 1).path("question").asText());
                independentCount++;
            }
        }
        assertThat(independentCount).isEqualTo(10);
        for (String language : new String[]{"es", "en"}) {
            var file = language.equals("es") ? "RagLiveQuestionsIT.java" : "RagLiveEnglishQuestionsIT.java";
            var source = Files.readString(Path.of("src/test/java/com/riansares/r4r/llm_tests", file));
            var block = source.split("CASES = List.of\\(", 2)[1].split("\n    \\);", 2)[0];
            var entries = Pattern.compile("new QuestionCase\\((.+)\\)").matcher(block);
            int number = 0;
            while (entries.find()) {
                var row = ordered.get(number++);
                var expected = JSON.writeValueAsString(row.path("name").asText()) + ", "
                        + JSON.writeValueAsString(row.path(language).asText()) + ", "
                        + (language.equals("en") ? JSON.writeValueAsString(row.path("es").asText()) + ", " : "")
                        + row.path("evidence").asBoolean();
                assertThat(entries.group(1)).isEqualTo(expected);
            }
            assertThat(number).isEqualTo(30);
        }
    }
}
