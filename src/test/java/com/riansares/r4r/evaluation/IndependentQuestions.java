package com.riansares.r4r.evaluation;

import java.util.List;

/** The twenty independent questions projected from the shared Spanish bank, in original ID order. */
final class IndependentQuestions {
    record Question(int id, String category, String question) { }
    static List<Question> load() throws Exception {
        var bank = EvaluationQuestions.document("es");
        var questions = new java.util.ArrayList<Question>();
        for (var row : bank.path("cases")) {
            if (row.has("independentId")) questions.add(new Question(row.path("independentId").asInt(),
                    row.path("category").asText(), row.path("question").asText()));
        }
        for (var row : bank.path("additionalIndependentQuestions")) questions.add(new Question(row.path("id").asInt(),
                row.path("category").asText(), row.path("question").asText()));
        questions.sort(java.util.Comparator.comparingInt(Question::id));
        return List.copyOf(questions);
    }
}
