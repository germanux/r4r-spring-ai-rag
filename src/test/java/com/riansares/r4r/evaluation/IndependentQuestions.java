package com.riansares.r4r.evaluation;

import java.util.List;

/** Independent questions retain their own IDs, independently of the bilingual bank order. */
final class IndependentQuestions {
    record Question(int id, String category, String question) { }
    static List<Question> load() throws Exception {
        var questions = new java.util.ArrayList<Question>();
        for (var row : EvaluationQuestions.independentRegressions().path("independent")) {
            questions.add(new Question(row.path("id").asInt(), row.path("category").asText(),
                    row.path("question").asText()));
        }
        return List.copyOf(questions);
    }
}
