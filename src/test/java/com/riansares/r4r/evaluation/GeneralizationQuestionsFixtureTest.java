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
                var status = com.riansares.r4r.rag.AnswerStatus.valueOf(i < 24 ? "ANSWERED" : i < 30 ? "PARTIAL" : "ABSTAINED");
                assertThat(cases.get(i).expectedStatus()).isEqualTo(status).isEqualTo(en.get(i).expectedStatus());
                if (i >= 24 && i < 30) {
                    assertThat(cases.get(i).acceptsStatus(com.riansares.r4r.rag.AnswerStatus.PARTIAL)).isTrue();
                    assertThat(cases.get(i).acceptsStatus(com.riansares.r4r.rag.AnswerStatus.ABSTAINED)).isTrue();
                    assertThat(cases.get(i).acceptsStatus(com.riansares.r4r.rag.AnswerStatus.ANSWERED)).isFalse();
                }
            }
            assertThat(EvaluationQuestions.timeline(language).question()).isNotBlank();
        }
    }

    @Test void newCumulativeQuestionsPreserveIndependentMultilineRequests() {
        for (String language : new String[]{"es", "en"}) {
            String first = EvaluationQuestions.caseById(language, "case-31").question();
            String second = EvaluationQuestions.caseById(language, "case-32").question();
            String third = EvaluationQuestions.caseById(language, "case-33").question();
            assertThat(second).startsWith(first + "\n");
            assertThat(third).startsWith(second + "\n");
            assertThat(second.lines().count()).isEqualTo(2);
            assertThat(third.lines().count()).isEqualTo(3);
        }
    }

    @Test void caseLookupUsesStableIdentityRatherThanDisplayNumber() {
        for (String language : new String[]{"es", "en"}) {
            var row = EvaluationQuestions.caseById(language, "case-31");
            assertThat(row.caseId()).isEqualTo("case-31");
            assertThat(row.number()).isEqualTo(20);
            assertThat(row.expectsEvidence()).isTrue();
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> EvaluationQuestions.caseById("es", "unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
