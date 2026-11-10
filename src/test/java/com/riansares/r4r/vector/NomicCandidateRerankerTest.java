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
}
