package com.riansares.r4r.vector;

import com.riansares.r4r.config.NomicRerankProperties;
import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import com.riansares.r4r.vector.s3vectors.*;
import com.riansares.r4r.vector.config.RagVectorProperties;
import java.time.Duration;
import java.util.*;
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
    static Stream<Arguments> canonicalCases() {
        return Stream.of("es", "en").flatMap(language ->
                com.riansares.r4r.evaluation.EvaluationQuestions.cases(language).stream()
                        .map(row -> Arguments.of(language, row.caseId())));
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("canonicalCases")
    void bothNomicAdaptersPreserveScoresBoundsAndRerankingForCurrentCases(String language, String caseId) {
        var current = com.riansares.r4r.evaluation.EvaluationQuestions.caseById(language, caseId);
        String question = current.question();
        // Recorded vectors are a transport/scoring fixture only. They are NOT embeddings of
        // the current question and this test makes no semantic-relevance assertion.
        var recorded=corpus.query("cave");var ranked=corpus.ranked(recorded);
        // Synthetic Qwen-sized wire vector; historical Nomic ranking fixtures remain unchanged.
        float[] vector=new float[1024];vector[0]=1;
        var vectors=mock(VectorStore.class);var jdbc=mock(JdbcTemplate.class);var aws=mock(S3VectorsClient.class);var embedding=mock(EmbeddingModel.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenAnswer(call->{
            SearchRequest request=call.getArgument(0);
            assertThat(request.getQuery()).isEqualTo(com.riansares.r4r.rag.ObservedEmbeddingModel.retrievalQuery(question));assertThat(request.getTopK()).isEqualTo(20);
            assertThat(request.getSimilarityThreshold()).isEqualTo(.63);
            return ranked.stream().filter(r->r.similarity()>.63).limit(request.getTopK()).map(r->
                    org.springframework.ai.document.Document.builder().id(r.row().id()).text(r.row().chunk().content())
                            .metadata(r.row().metadata()).score(r.similarity()).build()).toList();
        });
        when(embedding.embed(com.riansares.r4r.rag.ObservedEmbeddingModel.retrievalQuery(question))).thenReturn(vector);
        when(aws.queryVectors(any(QueryVectorsRequest.class))).thenAnswer(call->{
            QueryVectorsRequest request=call.getArgument(0);assertThat(request.topK()).isEqualTo(20);
            assertThat(request.indexName()).isEqualTo("amzn-r4r-vector-index");assertThat(request.queryVector().float32()).hasSize(1024);
            for(int i=0;i<1024;i++)assertThat(request.queryVector().float32().get(i)).isEqualTo(vector[i]);
            return QueryVectorsResponse.builder().distanceMetric(DistanceMetric.COSINE).vectors(ranked.stream().limit(request.topK())
                    .map(r->QueryOutputVector.builder().key(r.row().id()).metadata(NomicRegressionCorpus.document(r.row().metadata()))
                            .distance((float)(1-r.similarity())).build()).toList()).build();
        });
        var config=new RagVectorProperties.S3Vectors("eu-north-1","amzn-r4r-vectors","amzn-r4r-vector-index",Duration.ofSeconds(1),Duration.ofSeconds(3),Duration.ofSeconds(4));
        var pgResults=new PgVectorKnowledgeStore(vectors,jdbc).search(question,5,.63);
        var s3Results=new S3VectorsKnowledgeStore(aws,embedding,config,new S3VectorMetadataMapper(),new S3VectorScoreMapper()).search(question,5,.63);
        var admitted = ranked.stream().filter(row -> row.similarity() > .63).limit(20).toList();
        for(var selected:List.of(pgResults,s3Results)) {
            assertThat(selected).hasSize(5);
            assertThat(selected).allMatch(chunk -> admitted.stream()
                    .anyMatch(candidate -> candidate.row().chunk().equals(chunk)));
        }
        assertThat(pgResults).containsExactlyElementsOf(s3Results);
        var candidates = admitted.stream().map(row -> new NomicCandidateReranker.Candidate(
                row.row().chunk(), row.similarity())).toList();
        assertThat(pgResults).containsExactlyElementsOf(new NomicCandidateReranker(
                NomicRerankProperties.defaults()).select(question, candidates, 5));
        verify(embedding,times(1)).embed(com.riansares.r4r.rag.ObservedEmbeddingModel.retrievalQuery(question));verifyNoInteractions(jdbc);
    }
}
