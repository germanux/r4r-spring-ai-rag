package com.riansares.r4r.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/** User-supplied holdout, with no expected answers, relevance labels or corpus-dependent hints. */
final class IndependentQuestions {
    record Question(int id, String category, String question) { }
    static List<Question> load() throws Exception {
        try (var input=IndependentQuestions.class.getResourceAsStream("/evaluation/independent-questions-es.json")) {
            if(input==null)throw new IllegalStateException("Missing independent question fixture");
            return new ObjectMapper().readValue(input,new TypeReference<>() {});
        }
    }
}
