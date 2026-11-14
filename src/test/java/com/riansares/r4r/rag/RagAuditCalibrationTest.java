package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.chunking.MarkdownChunk;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression guards for neutral, question-scoped RAG auditing. No LLM or DB calls. */
class RagAuditCalibrationTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private RagAuditService service() {
        return new RagAuditService(false, null, null, null, Duration.ofSeconds(1), json);
    }

    private String result(AnswerStatus original, AnswerStatus recommended, String verdict,
                          String reason, String note) throws Exception {
        return json.writeValueAsString(Map.of(
                "originalStatus", original.name(),
                "recommendedStatus", recommended.name(),
                "verdict", verdict,
                "reason", reason,
                "note", note));
    }

    @Test
    void properlySupportedAnswerRemainsAnsweredWithPass() throws Exception {
        try (var service = service()) {
            var decision = service.validate(result(AnswerStatus.ANSWERED, AnswerStatus.ANSWERED,
                    "PASS", "The cited text explains the requested distinction.", ""), AnswerStatus.ANSWERED);
            assertThat(decision.conclusive()).isTrue();
            assertThat(decision.effectiveStatus()).isEqualTo(AnswerStatus.ANSWERED);
            assertThat(decision.metadata().verdict()).isEqualTo("PASS");
        }
    }

    @Test
    void materialUnsupportedClaimCanStillBeDowngraded() throws Exception {
        try (var service = service()) {
            var decision = service.validate(result(AnswerStatus.ANSWERED, AnswerStatus.PARTIAL,
                    "FAIL", "The evidence gives no structural assessment of this property.",
                    "A site-specific structural conclusion is not established."), AnswerStatus.ANSWERED);
            assertThat(decision.conclusive()).isTrue();
            assertThat(decision.effectiveStatus()).isEqualTo(AnswerStatus.PARTIAL);
            assertThat(decision.metadata().verdict()).isEqualTo("FAIL");
        }
    }

    @Test
    void uncertainVerdictIsAcceptedAsNeutralAndNotReportedAsBadJson() throws Exception {
        try (var service = service()) {
            var decision = service.validate(result(AnswerStatus.ANSWERED, AnswerStatus.ANSWERED,
                    "INCONCLUSIVE", "The available cited evidence cannot settle the disputed claim.", ""),
                    AnswerStatus.ANSWERED);
            assertThat(decision.conclusive()).isFalse();
            assertThat(decision.effectiveStatus()).isEqualTo(AnswerStatus.ANSWERED);
            assertThat(decision.metadata().verdict()).isEqualTo("INCONCLUSIVE");
            assertThat(decision.metadata().reason()).contains("cannot settle");
        }
    }

    @Test
    void uncertainVerdictCannotDowngradeOrAddCustomerNote() throws Exception {
        try (var service = service()) {
            var decision = service.validate(result(AnswerStatus.ANSWERED, AnswerStatus.PARTIAL,
                    "INCONCLUSIVE", "There is not enough information.", ""), AnswerStatus.ANSWERED);
            assertThat(decision.conclusive()).isFalse();
            assertThat(decision.effectiveStatus()).isEqualTo(AnswerStatus.ANSWERED);
            assertThat(decision.metadata()).isNull();
        }
    }

    @Test
    void failWithoutDowngradeAndFailWithoutNoteAreRejected() throws Exception {
        try (var service = service()) {
            var sameStatusFail = service.validate(result(AnswerStatus.ANSWERED, AnswerStatus.ANSWERED,
                    "FAIL", "An unsupported claim was found.", "A material claim is unsupported."),
                    AnswerStatus.ANSWERED);
            var noteMissing = service.validate(result(AnswerStatus.ANSWERED, AnswerStatus.PARTIAL,
                    "FAIL", "An unsupported claim was found.", ""), AnswerStatus.ANSWERED);
            assertThat(sameStatusFail.conclusive()).isFalse();
            assertThat(noteMissing.conclusive()).isFalse();
        }
    }
    @Test
    void promptDoesNotFavorFailOrDemandUnrequestedDetail() {
        try (var service = service()) {
            var snapshot = RagEvidenceSnapshot.capture("How does the base differ from the finish?", "en",
                    RagResult.abstain(),
                    List.of(new MarkdownChunk("en/guide.md", List.of("Floors"), 1, "Base is beneath the finish.")),
                    null, RagProgress.silent(), "no_evidence", 1, 5, .63);
            String prompt = service.prompt(snapshot).getContents();
            assertThat(prompt).contains("user's actual question", "same evidence standard", "INCONCLUSIVE");
            assertThat(prompt).doesNotContain("Prioritize justified downgrades");
        }
    }

}
