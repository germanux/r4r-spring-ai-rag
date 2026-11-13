package com.riansares.r4r.evaluation;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndependentQuestionsFixtureTest {
    @Test void independentRunnerUsesTheCurrentSpanishBankWithStableCaseIds() {
        var questions = EvaluationQuestions.cases("es");
        assertThat(questions).hasSize(35);
        assertThat(questions).extracting(EvaluationQuestions.QuestionCase::caseId).doesNotHaveDuplicates();
        assertThat(questions).extracting(EvaluationQuestions.QuestionCase::question)
                .doesNotHaveDuplicates().allMatch(q -> !q.isBlank());
        for (var row : questions) {
            assertThat(EvaluationQuestions.caseById("es", row.caseId())).isEqualTo(row);
        }
    }
    @Test void recorderPreservesQuestionOrderEvidenceAndReadOnlyBoundary(){
        var delegate=mock(com.riansares.r4r.vector.port.KnowledgeVectorStore.class);
        var chunks=List.of(new com.riansares.r4r.chunking.MarkdownChunk("sample.md",List.of("Heading"),2,"Original evidence"));
        String question=EvaluationQuestions.caseById("es", "case-24").question();
        when(delegate.search(question,5,.63)).thenReturn(chunks);
        var recorder=new RecordingKnowledgeStore(delegate);
        assertThat(recorder.search(question,5,.63)).isSameAs(chunks);
        assertThat(recorder.searches.getFirst()).containsEntry("query",question);
        assertThat(recorder.searches.getFirst().get("fragments")).isEqualTo(List.of(Map.of("label","[S1]","chunk",chunks.getFirst())));
        assertThatThrownBy(()->recorder.index(chunks)).isInstanceOf(UnsupportedOperationException.class);
        verify(delegate).search(question,5,.63);verifyNoMoreInteractions(delegate);
    }
}
