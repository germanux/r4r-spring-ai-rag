package com.riansares.r4r.evaluation;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GeneralizationQuestionsFixtureTest {
    @Test void bilingualQuestionBanksPreserveOrderAndEvidenceContract() {
        var es = EvaluationQuestions.cases("es");
        var en = EvaluationQuestions.cases("en");
        assertThat(es).hasSize(35);
        assertThat(en).hasSize(35);
        for (String language : new String[]{"es", "en"}) {
            var identities = new HashSet<String>();
            var cases = EvaluationQuestions.cases(language);
            for (int i = 0; i < cases.size(); i++) {
                assertThat(identities.add(cases.get(i).caseId())).isTrue();
                assertThat(cases.get(i).caseId()).isEqualTo(es.get(i).caseId());
                assertThat(cases.get(i).number()).isEqualTo(i + 1);
                assertThat(cases.get(i).question()).isNotBlank();
                assertThat(cases.get(i).retrievalQuestion()).isEqualTo(es.get(i).question());
                assertThat(cases.get(i).expectsEvidence()).isEqualTo(i < 24);
            }
            assertThat(EvaluationQuestions.timeline(language).question()).isNotBlank();
        }
    }

    @Test void newCumulativeQuestionsPreserveIndependentMultilineRequests() {
        for (String language : new String[]{"es", "en"}) {
            var cases = EvaluationQuestions.cases(language);
            String first = questionById(cases, "case-31");
            String second = questionById(cases, "case-32");
            String third = questionById(cases, "case-33");
            assertThat(second).startsWith(first + "\n");
            assertThat(third).startsWith(second + "\n");
            assertThat(second.lines().count()).isEqualTo(2);
            assertThat(third.lines().count()).isEqualTo(3);
        }
    }

    @Test void recordedNomicQueriesRemainIndependentOfTheReorderedBanks() {
        for (String language : new String[]{"es", "en"}) {
            var recorded = EvaluationQuestions.independentRegressions().path("nomic").path(language);
            assertThat(recorded.size()).isEqualTo(15);
            for (int i = 0; i < recorded.size(); i++) {
                assertThat(recorded.get(i).path("number").asInt()).isEqualTo(i + 1);
                assertThat(recorded.get(i).path("question").asText()).isNotBlank();
                assertThat(recorded.get(i).path("retrievalQuestion").asText()).isNotBlank();
            }
        }
    }

    private static String questionById(java.util.List<EvaluationQuestions.QuestionCase> cases, String id) {
        return cases.stream().filter(row -> row.caseId().equals(id)).findFirst().orElseThrow().question();
    }
}
