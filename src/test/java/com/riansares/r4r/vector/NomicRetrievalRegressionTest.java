package com.riansares.r4r.vector;

import com.riansares.r4r.config.NomicRerankProperties;
import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import com.riansares.r4r.vector.s3vectors.*;
import com.riansares.r4r.vector.config.RagVectorProperties;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.*;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NomicRetrievalRegressionTest {
    static NomicRegressionCorpus corpus;
    @BeforeAll static void load() throws Exception { corpus=new NomicRegressionCorpus(); }
    static Stream<Arguments> caveCases() { return Stream.of(
            Arguments.of("cave","¿Con qué reformaríais una cueva?",19),
            Arguments.of("cellar","¿Con qué reformaríais una bodega?",6),
            Arguments.of("mortar","¿Qué mortero utilizaríais para rehabilitar una cueva antigua?",3)); }
    @ParameterizedTest @MethodSource("caveCases")
    void bothNomicAdaptersRecoverOriginalCaseWithoutChangingEmbeddingsOrThreshold(String key,String question,int originalRank) {
        var vector=corpus.query(key);var ranked=corpus.ranked(vector);
        int rank=0;while(ranked.get(rank).row().chunk().headingPath().stream().noneMatch(h->h.contains("Caso G")))rank++;
        assertThat(rank+1).isEqualTo(originalRank);
        var vectors=mock(VectorStore.class);var jdbc=mock(JdbcTemplate.class);var aws=mock(S3VectorsClient.class);var embedding=mock(EmbeddingModel.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenAnswer(call->{
            SearchRequest request=call.getArgument(0);
            assertThat(request.getQuery()).isEqualTo(question);assertThat(request.getTopK()).isEqualTo(20);
            assertThat(request.getSimilarityThreshold()).isEqualTo(.63);
            return ranked.stream().filter(r->r.similarity()>.63).limit(request.getTopK()).map(r->
                    org.springframework.ai.document.Document.builder().id(r.row().id()).text(r.row().chunk().content())
                            .metadata(r.row().metadata()).score(r.similarity()).build()).toList();
        });
        when(embedding.embed(question)).thenReturn(vector);
        when(aws.queryVectors(any(QueryVectorsRequest.class))).thenAnswer(call->{
            QueryVectorsRequest request=call.getArgument(0);assertThat(request.topK()).isEqualTo(20);
            assertThat(request.indexName()).isEqualTo("amzn-r4r-vector-index");assertThat(request.queryVector().float32()).hasSize(768);
            for(int i=0;i<768;i++)assertThat(request.queryVector().float32().get(i)).isEqualTo(vector[i]);
            return QueryVectorsResponse.builder().distanceMetric(DistanceMetric.COSINE).vectors(ranked.stream().limit(request.topK())
                    .map(r->QueryOutputVector.builder().key(r.row().id()).metadata(NomicRegressionCorpus.document(r.row().metadata()))
                            .distance((float)(1-r.similarity())).build()).toList()).build();
        });
        var config=new RagVectorProperties.S3Vectors("eu-north-1","amzn-r4r-vectors","amzn-r4r-vector-index",Duration.ofSeconds(1),Duration.ofSeconds(3),Duration.ofSeconds(4));
        var pgResults=new PgVectorKnowledgeStore(vectors,jdbc).search(question,5,.63);
        var s3Results=new S3VectorsKnowledgeStore(aws,embedding,config,new S3VectorMetadataMapper(),new S3VectorScoreMapper()).search(question,5,.63);
        for(var selected:List.of(pgResults,s3Results)) {
            assertThat(selected).hasSize(5);
            assertThat(selected).anySatisfy(chunk->{assertThat(chunk.source()).isEqualTo("R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md");
                assertThat(chunk.headingPath()).anyMatch(h->h.startsWith("12. Caso G"));assertThat(chunk.content()).containsIgnoringCase("cal");});
        }
        assertThat(pgResults).containsExactlyElementsOf(s3Results);
        verify(embedding,times(1)).embed(question);verifyNoInteractions(jdbc);
    }
    static Stream<Arguments> canonicalCases() throws Exception {
        try(var stream=NomicRetrievalRegressionTest.class.getResourceAsStream("/retrieval/nomic-captured-questions.json")) {
            var rows=NomicRegressionCorpus.JSON.readTree(stream);var cases=new ArrayList<Arguments>();
            for(var row:rows)cases.add(Arguments.of(row.path("lang").asText(),row.path("question").asText(),row.path("evidence").asBoolean(),
                    String.format(Locale.ROOT,"%s-%02d",row.path("lang").asText(),row.path("number").asInt()),row.path("retrievalQuestion").asText()));
            return cases.stream();
        }
    }
    @ParameterizedTest @MethodSource("canonicalCases")
    void bilingualCandidatesRetainBestExistingEvidenceAndRawThreshold(String language,String question,boolean answerable,String key,String retrieval) throws Exception {
        var ranked=corpus.ranked(corpus.query(key)).stream().filter(r->r.similarity()>.63).toList();
        var original=ranked.stream().limit(5).map(r->r.row().chunk()).toList();
        var candidates=ranked.stream().limit(20).map(r->new NomicCandidateReranker.Candidate(r.row().chunk(),r.similarity())).toList();
        var selected=new NomicCandidateReranker(NomicRerankProperties.defaults()).select(retrieval,candidates,5);
        assertThat(selected).hasSize(original.size());
        if(!original.isEmpty())assertThat(selected).contains(original.getFirst());
        assertThat(selected).allMatch(chunk->candidates.stream().anyMatch(c->c.chunk().equals(chunk)&&c.similarity()>.63));
        if(!answerable) {
            assertThat(selected.isEmpty()).isEqualTo(original.isEmpty()); // Confidence admission/empty evidence never expands.
            var model=mock(org.springframework.ai.chat.model.ChatModel.class);
            when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(new org.springframework.ai.chat.model.ChatResponse(List.of(
                    new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage("R4R_ABSTAIN")))));
            var store=mock(com.riansares.r4r.vector.port.KnowledgeVectorStore.class);when(store.search(anyString(),eq(5),eq(.63))).thenReturn(selected);
            var result=new com.riansares.r4r.rag.CitedRagService(store,model,5,.63).answer(question,retrieval,language);
            assertThat(result.abstention()).isTrue();assertThat(result.citations()).isEmpty();
            if(selected.isEmpty())verifyNoInteractions(model);
        }
    }
}
