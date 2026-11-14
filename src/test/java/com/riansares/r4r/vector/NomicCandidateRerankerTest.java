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
        var first=candidate("same first",.7);var second=candidate("same other",.7);
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

    @Test void expandedParentDoesNotConsumeAnotherSlotForItsIdenticalChild() {
        var child = new MarkdownChunk("guide.md", List.of("Guide", "Initial guidance"), 1,
                "## Initial guidance\nSupply location, photos and measurements.");
        var parent = new MarkdownChunk("guide.md", List.of("Guide"), 0,
                "# Guide\n" + child.content());
        var general = new MarkdownChunk("general.md", List.of("General intake"), 1, "Provide scope and timeframe.");
        var rows = List.of(new NomicCandidateReranker.Candidate(parent,.8),
                new NomicCandidateReranker.Candidate(child,.79),new NomicCandidateReranker.Candidate(general,.78));
        var semanticOrder = new NomicCandidateReranker(new NomicRerankProperties(4,0));
        assertThat(semanticOrder.select("initial guidance",rows,2)).containsExactly(parent,general);
        assertThat(semanticOrder.select("initial guidance",rows,5)).containsExactly(parent,general);
    }

    @Test void differentChildrenAndSourcesAreNotCollapsed() {
        var a = new MarkdownChunk("guide.md", List.of("Guide","A"),1,"Shared words, distinct first evidence.");
        var b = new MarkdownChunk("guide.md", List.of("Guide","B"),2,"Shared words, distinct second evidence.");
        var c = new MarkdownChunk("other.md",List.of("Guide","A"),1,a.content());
        var rows = List.of(a,b,c).stream().map(chunk -> new NomicCandidateReranker.Candidate(chunk,.8)).toList();
        assertThat(reranker.select("shared words",rows,3)).containsExactly(a,b,c);
    }

    @Test void cafeMatchesHospitalityChecksButNotAnUnrelatedShopPartitionExample() {
        String query = "I want to turn a shop into a café. What building work do I need before opening?";
        assertThat(NomicCandidateReranker.supportsPartialTopic(query,
                candidate("Before renovating hospitality premises, check use, occupancy, extraction and fire safety.",.8).chunk())).isTrue();
        assertThat(NomicCandidateReranker.supportsPartialTopic(query,
                candidate("A shop wants to remove a partition. First check stability and installations.",.8).chunk())).isFalse();
        assertThat(NomicCandidateReranker.supportsPartialTopic("Quiero pasar de tienda a cafetería. ¿Qué obras necesito para abrir?",
                candidate("Antes de reformar un local de hostelería, revisar uso, aforo, extracción y saneamiento.",.8).chunk())).isTrue();
    }

    @Test void equivalentInitialGuidancePhrasesProduceTheSameLexicalRanking() {
        var rows = List.of(candidate("La orientación inicial tiene límites.",.71),
                candidate("Datos para una primera orientación: fotos y medidas.",.71),candidate("Unrelated topic.",.71));
        assertThat(reranker.select("orientación inicial",rows,1))
                .containsExactlyElementsOf(reranker.select("primera orientación",rows,1));
    }

}
