package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.config.NomicRerankProperties;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NomicCandidateRerankerTest {
    final NomicCandidateReranker reranker=new NomicCandidateReranker(NomicRerankProperties.defaults());
    NomicCandidateReranker.Candidate candidate(String text,double score) {
        return new NomicCandidateReranker.Candidate(new MarkdownChunk("source.md",List.of("Section"),text.hashCode()&0x7fffffff,text),score);
    }
    @Test void boundedCandidatesAndInvalidSettings() {
        assertThat(reranker.candidateCount(5)).isEqualTo(20);assertThat(reranker.candidateCount(Integer.MAX_VALUE)).isEqualTo(10_000);
        for(int multiplier:List.of(0,11))assertThatThrownBy(()->new NomicRerankProperties(multiplier,.075)).isInstanceOf(IllegalArgumentException.class);
        for(double weight:new double[]{Double.NaN,-.1,.3})assertThatThrownBy(()->new NomicRerankProperties(4,weight)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void noLexicalEvidenceAndZeroWeightRetainSemanticOrder() {
        var rows=List.of(candidate("general",.72),candidate("specific",.64));
        assertThat(reranker.select("absent",rows,1)).containsExactly(rows.getFirst().chunk());
        assertThat(new NomicCandidateReranker(new NomicRerankProperties(1,0)).select("specific",rows,1)).containsExactly(rows.getFirst().chunk());
        assertThat(reranker.select("specific",List.of(),5)).isEmpty();
    }
    @Test void standardNormalizationUsesActualTermsWithoutSynonymsOrDomainRules() {
        var rows=List.of(candidate("general unrelated",.70),candidate("HÚMEDAD técnica",.69));
        assertThat(reranker.select("humedad",rows,1)).containsExactly(rows.getLast().chunk());
        assertThat(reranker.select("moisture",rows,1)).containsExactly(rows.getFirst().chunk());
    }
    @Test void stableTiesAndExistingResultLimit() {
        var first=candidate("same",.7);var second=candidate("same",.7);
        assertThat(reranker.select("same",List.of(first,second),1)).containsExactly(first.chunk());
        assertThat(reranker.select("same",List.of(first,second),5)).containsExactly(first.chunk(),second.chunk());
    }
    @Test void grammaticalOverlapCannotOutrankTheActualSubject() {
        var functionWords=candidate("como si en algunas hay un pero me",.705);
        var subject=candidate("grietas inspeccion estructural",.70);
        var ranker=new NomicCandidateReranker(new NomicRerankProperties(8,.10));
        assertThat(ranker.select("como si hay grietas en algunas",List.of(functionWords,subject),1))
                .containsExactly(subject.chunk());
        var englishFunctionWords=candidate("the what is how can you your in on a an and",.705);
        var englishSubject=candidate("cracks structural inspection",.70);
        assertThat(ranker.select("how can you inspect the cracks",List.of(englishFunctionWords,englishSubject),1))
                .containsExactly(englishSubject.chunk());
    }
    @Test void partialTopicSupportRejectsSuperficialSharedWordsButRetainsSpecificChecks() {
        assertThat(NomicCandidateReranker.supportsPartialTopic("Tengo un pasillo largo y oscuro. ¿Cómo gano luz y aprovecho el espacio?",
                candidate("El extractor del baño debe asegurar ventilación adecuada. Puede conectarse a la luz.", .8).chunk())).isFalse();
        assertThat(NomicCandidateReranker.supportsPartialTopic("Our office has hot and cold areas. Can climate control be renovated?",
                candidate("Office renovation requires planning phases, access, and working hours.", .8).chunk())).isFalse();
        assertThat(NomicCandidateReranker.supportsPartialTopic("Las vigas de madera de mi casa están combadas. ¿Se refuerzan?",
                candidate("Revisar las vigas y los apoyos de madera antes de tomar decisiones estructurales.", .8).chunk())).isTrue();
    }

}
