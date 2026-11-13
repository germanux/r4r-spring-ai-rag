package com.riansares.r4r.evaluation;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GeneralizationQuestionsFixtureTest {
    @Test void bilingualQuestionBanksPreserveOrderCoverageAndHistoricalIdentity() {
        var es = EvaluationQuestions.cases("es");
        var en = EvaluationQuestions.cases("en");
        assertThat(es).hasSize(35);
        assertThat(en).hasSize(35);
        for (String language : new String[]{"es", "en"}) {
            var bank = EvaluationQuestions.document(language);
            var identities = new HashSet<String>();
            var cases = EvaluationQuestions.cases(language);
            assertThat(bank.path("history").path("generalization").size()).isEqualTo(30);
            assertThat(bank.path("history").path("nomic").size()).isEqualTo(15);
            for (int i = 0; i < cases.size(); i++) {
                var row = bank.path("cases").get(i);
                assertThat(identities.add(cases.get(i).caseId())).isTrue();
                assertThat(cases.get(i).caseId()).isEqualTo(es.get(i).caseId());
                assertThat(cases.get(i).number()).isEqualTo(i + 1);
                assertThat(cases.get(i).question()).isNotBlank();
                assertThat(cases.get(i).retrievalQuestion()).isEqualTo(es.get(i).question());
                assertThat(cases.get(i).expectsEvidence()).isEqualTo(i < 19 || i >= 30);
                assertThat(row.path("coverage").asText()).isIn("SUPPORTED", "PARTIAL", "INSUFFICIENT");
                assertThat(row.path("coverageRationale").asText()).isNotBlank();
                if (i < 30) {
                    var original = bank.path("history").path("generalization").get(row.path("initialNumber").asInt() - 1);
                    assertThat(cases.get(i).caseId()).isEqualTo(original.path("caseId").asText());
                    if (!row.has("revision")) assertThat(cases.get(i).question()).isEqualTo(original.path("question").asText());
                } else {
                    assertThat(cases.get(i).caseId()).isEqualTo("case-" + (i + 1));
                    assertThat(row.path("origin").asText()).isEqualTo("post-bilingual-2026-11-13");
                    assertThat(row.path("coverage").asText()).isEqualTo("SUPPORTED");
                    assertThat(row.path("coverageReferences").isArray()).isTrue();
                    assertThat(row.path("coverageReferences").size()).isGreaterThan(0);
                    for (var reference : row.path("coverageReferences")) {
                        var path = java.nio.file.Path.of(reference.asText().split(" — ", 2)[0]);
                        assertThat(path).exists();
                    }
                }
            }
            assertThat(EvaluationQuestions.timeline(language).question()).isNotBlank();
        }
    }

    @Test void newCumulativeQuestionsPreserveIndependentMultilineRequests() {
        for (String language : new String[]{"es", "en"}) {
            var cases = EvaluationQuestions.cases(language);
            String first = cases.get(30).question();
            String second = cases.get(31).question();
            String third = cases.get(32).question();
            assertThat(second).startsWith(first + "\n");
            assertThat(third).startsWith(second + "\n");
            assertThat(second.lines().count()).isEqualTo(2);
            assertThat(third.lines().count()).isEqualTo(3);
        }
    }

    @Test void independentQuestionsKeepOriginalIdsAndOnlyEvenWordingIsShortened() throws Exception {
        var current = IndependentQuestions.load();
        var historical = EvaluationQuestions.document("es").path("history").path("independentOriginal");
        assertThat(current).hasSize(20);
        assertThat(historical.size()).isEqualTo(20);
        for (int i = 0; i < current.size(); i++) {
            var original = historical.get(i);
            assertThat(current.get(i).id()).isEqualTo(original.path("id").asInt()).isEqualTo(i + 1);
            assertThat(current.get(i).category()).isEqualTo(original.path("category").asText());
            if ((i + 1) % 2 == 0) {
                assertThat(current.get(i).question().length()).isLessThan(original.path("question").asText().length());
            } else {
                assertThat(current.get(i).question()).isEqualTo(original.path("question").asText());
            }
        }
    }
}
