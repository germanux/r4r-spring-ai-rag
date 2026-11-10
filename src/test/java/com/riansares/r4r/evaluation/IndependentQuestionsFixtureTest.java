package com.riansares.r4r.evaluation;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndependentQuestionsFixtureTest {
    @Test void holdsTwentyUniqueUnlabelledQuestionsInOriginalOrder() throws Exception {
        var questions=IndependentQuestions.load();assertThat(questions).hasSize(20);
        assertThat(questions).extracting(IndependentQuestions.Question::id).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1,20).boxed().toList());
        assertThat(questions).extracting(IndependentQuestions.Question::question).doesNotHaveDuplicates().allMatch(q->!q.isBlank());
        assertThat(questions.get(18).question()).isEqualTo("¿Sale más barato arreglar lo que tengo o hacerlo nuevo?");
        assertThat(questions.stream().collect(java.util.stream.Collectors.groupingBy(IndependentQuestions.Question::category,java.util.stream.Collectors.counting())))
                .containsEntry("Viviendas",8L).containsEntry("Oficinas y locales",4L).containsEntry("Otros edificios e instalaciones",5L).containsEntry("Preguntas ambiguas o poco precisas",3L);
    }
    @Test void recorderPreservesQuestionOrderEvidenceAndReadOnlyBoundary(){
        var delegate=mock(com.riansares.r4r.vector.port.KnowledgeVectorStore.class);
        var chunks=List.of(new com.riansares.r4r.chunking.MarkdownChunk("sample.md",List.of("Heading"),2,"Original evidence"));
        String question="¿Sale más barato arreglar lo que tengo o hacerlo nuevo?";
        when(delegate.search(question,5,.63)).thenReturn(chunks);
        var recorder=new RecordingKnowledgeStore(delegate);
        assertThat(recorder.search(question,5,.63)).isSameAs(chunks);
        assertThat(recorder.searches.getFirst()).containsEntry("query",question);
        assertThat(recorder.searches.getFirst().get("fragments")).isEqualTo(List.of(Map.of("label","[S1]","chunk",chunks.getFirst())));
        assertThatThrownBy(()->recorder.index(chunks)).isInstanceOf(UnsupportedOperationException.class);
        verify(delegate).search(question,5,.63);verifyNoMoreInteractions(delegate);
    }
}
