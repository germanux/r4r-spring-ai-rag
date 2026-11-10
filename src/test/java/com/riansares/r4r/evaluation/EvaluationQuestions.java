package com.riansares.r4r.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** One question bank per language, shared by live, simulated and historical replay tests. */
public final class EvaluationQuestions {
    private EvaluationQuestions() { }

    public record QuestionCase(String caseId, int number, String name, String question,
                               String retrievalQuestion, boolean expectsEvidence) { }

    public static JsonNode document(String language) {
        if (!List.of("es", "en").contains(language)) throw new IllegalArgumentException("Unsupported evaluation language");
        try (var input = EvaluationQuestions.class.getResourceAsStream("/evaluation/questions-" + language + ".json")) {
            if (input == null) throw new IllegalStateException("Missing evaluation questions: " + language);
            return new ObjectMapper().readTree(input);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read evaluation questions: " + language, failure);
        }
    }

    public static List<QuestionCase> cases(String language) {
        var spanish = document("es").path("cases");
        var result = new ArrayList<QuestionCase>();
        for (var row : document(language).path("cases")) {
            String retrieval = null;
            for (var es : spanish) {
                if (es.path("caseId").equals(row.path("caseId"))) retrieval = es.path("question").asText();
            }
            if (retrieval == null) throw new IllegalStateException("Unpaired evaluation question");
            result.add(new QuestionCase(row.path("caseId").asText(), row.path("number").asInt(),
                    row.path("name").asText(), row.path("question").asText(), retrieval, row.path("evidence").asBoolean()));
        }
        return List.copyOf(result);
    }

    public static QuestionCase timeline(String language) {
        var row = document(language).path("regressions").path("timeline");
        return new QuestionCase("timeline-reason", 31, row.path("name").asText(), row.path("question").asText(),
                document("es").path("regressions").path("timeline").path("question").asText(), row.path("evidence").asBoolean());
    }
}
