package com.riansares.r4r.rag;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RagResultTest {
    @Test void abstentionStatusKeepsTheLegacyContract() {
        var result = RagResult.abstain();
        assertThat(result.status()).isEqualTo(AnswerStatus.ABSTAINED);
        assertThat(result.abstention()).isTrue();
        assertThat(result.answer()).isEmpty();
        assertThat(result.citations()).isEmpty();
    }
    @Test void inconsistentStatusAndEmptyPartialAnswersAreRejected() {
        assertThatThrownBy(() -> new RagResult("", false, List.of(), AnswerStatus.ABSTAINED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagResult("unsupported", true, List.of(), AnswerStatus.ABSTAINED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RagResult.ofAnswer("unsupported", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagResult("unsupported", false, List.of(), AnswerStatus.PARTIAL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
