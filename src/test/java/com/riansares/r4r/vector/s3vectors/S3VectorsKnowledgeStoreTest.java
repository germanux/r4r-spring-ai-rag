package com.riansares.r4r.vector.s3vectors;

import com.riansares.r4r.vector.config.RagVectorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class S3VectorsKnowledgeStoreTest {
    private final S3VectorsClient client = mock(S3VectorsClient.class);
    private final EmbeddingModel embedding = mock(EmbeddingModel.class);
    private final S3VectorsKnowledgeStore store = new S3VectorsKnowledgeStore(client, embedding,
            new RagVectorProperties.S3Vectors("eu-north-1", "test-vector-bucket", "test-vector-index",
                    Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(4)),
            new S3VectorMetadataMapper(), new S3VectorScoreMapper());

    @BeforeEach void embedding() {
        float[] vector = new float[768]; vector[0] = 1;
        when(embedding.embed(anyString())).thenReturn(vector);
    }

    @Test void paginatesOnceEmbeddedAndReturnsOrderedThresholdedCompleteChunks() {
        when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(
                page("next", hit("b", .2f), hit("excluded", .8f)), page(null, hit("a", .1f)));
        var results = store.search("pregunta original", 3, .63);
        assertThat(results).extracting(c -> c.source()).containsExactly("a.md", "b.md");
        assertThat(results.getFirst().content()).isEqualTo("Evidence a.");
        assertThat(results.getFirst().headingPath()).containsExactly("Guide", "Section");
        assertThat(results.getFirst().index()).isEqualTo(2);
        var requests = org.mockito.ArgumentCaptor.forClass(QueryVectorsRequest.class);
        verify(client, times(2)).queryVectors(requests.capture());
        var first = requests.getAllValues().getFirst();
        var second = requests.getAllValues().getLast();
        assertThat(first.vectorBucketName()).isEqualTo("test-vector-bucket");
        assertThat(first.indexName()).isEqualTo("test-vector-index");
        assertThat(first.returnDistance()).isTrue(); assertThat(first.returnMetadata()).isTrue();
        assertThat(first.topK()).isEqualTo(3);
        assertThat(first.queryVector().float32()).hasSize(768);
        assertThat(first.nextToken()).isNull(); assertThat(second.nextToken()).isEqualTo("next");
        assertThat(second.queryVector()).isEqualTo(first.queryVector());
        assertThat(second.toBuilder().nextToken((String) null).build()).isEqualTo(first);
        verify(embedding, times(1)).embed("pregunta original");
        verifyNoMoreInteractions(client);
    }

    @Test void validEmptyAndBelowThresholdAreEmptyWithoutAnotherEmbedding() {
        when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(page(null), page(null, hit("low", 1f)));
        assertThat(store.search("q", 5, .63)).isEmpty();
        assertThat(store.search("q", 5, .63)).isEmpty();
        verify(embedding, times(2)).embed("q");
    }

    @Test void invalidInputsDoNotInvokeEmbeddingOrAws() {
        for (String query : new String[]{null, "", "  "}) {
            assertThatThrownBy(() -> store.search(query, 5, .63)).isInstanceOf(IllegalArgumentException.class);
        }
        for (int topK : new int[]{0, -1, 10001}) {
            assertThatThrownBy(() -> store.search("q", topK, .63)).isInstanceOf(IllegalArgumentException.class);
        }
        for (double min : new double[]{-.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> store.search("q", 5, min)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(embedding, client);
    }

    @Test void wrongDimensionNonFiniteAndZeroEmbeddingsAreErrorsBeforeAws() {
        float[] nan = new float[768]; nan[0] = Float.NaN;
        float[] infinite = new float[768]; infinite[0] = Float.POSITIVE_INFINITY;
        for (float[] value : new float[][]{null, new float[767], new float[768], nan, infinite}) {
            when(embedding.embed("q")).thenReturn(value);
            assertThatThrownBy(() -> store.search("q", 5, .63)).isInstanceOf(IllegalStateException.class);
        }
        verifyNoInteractions(client);
    }

    @Test void propagatesAwsAndEmbeddingErrorsIncludingFailureAfterFirstPage() {
        var failure = ServiceUnavailableException.builder().message("simulated").build();
        when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(page("next", hit("a", .1f))).thenThrow(failure);
        assertThatThrownBy(() -> store.search("q", 5, .63)).isSameAs(failure);
        verify(embedding).embed("q");
        reset(client);
        var embeddingFailure = new IllegalStateException("embedding unavailable");
        when(embedding.embed("q")).thenThrow(embeddingFailure);
        assertThatThrownBy(() -> store.search("q", 5, .63)).isSameAs(embeddingFailure);
        verifyNoInteractions(client);
    }

    @Test void invalidResponsesAreNeverReportedAsNoEvidence() {
        var missingMetadata = QueryOutputVector.builder().key("a").distance(.1f).build();
        var missingDistance = hit("a", .1f).toBuilder().distance(null).build();
        var nan = hit("a", Float.NaN);
        for (var response : List.of(page(null, missingMetadata), page(null, missingDistance), page(null, nan),
                page(null).toBuilder().distanceMetric(DistanceMetric.EUCLIDEAN).build(),
                page(null).toBuilder().distanceMetric((String) null).build(),
                page(null, hit("a", .1f), hit("a", .2f)))) {
            when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(response);
            assertThatThrownBy(() -> store.search("q", 5, .63)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void rejectsRepeatedTokensExcessResultsAndUnboundedEmptyPages() {
        when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(page("repeat"));
        assertThatThrownBy(() -> store.search("q", 5, .63)).hasMessageContaining("pagination token");
        when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(page(null, hit("a", .1f), hit("b", .2f)));
        assertThatThrownBy(() -> store.search("q", 1, .63)).hasMessageContaining("topK");
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        when(client.queryVectors(any(QueryVectorsRequest.class))).thenAnswer(i -> page("token" + counter.incrementAndGet()));
        assertThatThrownBy(() -> store.search("q", 1, .63)).hasMessageContaining("bounded");
    }

    @Test void equalDistancesRetainReceivedOrder() {
        when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(page(null, hit("b", .1f), hit("a", .1f)));
        assertThat(store.search("q", 2, .63)).extracting(c -> c.source()).containsExactly("b.md", "a.md");
    }

    @Test void writesAndCancellationNeverCallInfrastructure() {
        assertThatThrownBy(() -> store.index(List.of())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> store.replaceSource("a", List.of())).isInstanceOf(UnsupportedOperationException.class);
        Thread.currentThread().interrupt();
        try { assertThatThrownBy(() -> store.search("q", 5, .63)).isInstanceOf(CancellationException.class); }
        finally { Thread.interrupted(); }
        verifyNoInteractions(embedding, client);
    }

    private static QueryVectorsResponse page(String token, QueryOutputVector... hits) {
        return QueryVectorsResponse.builder().distanceMetric(DistanceMetric.COSINE).vectors(hits).nextToken(token).build();
    }

    private static QueryOutputVector hit(String key, float distance) {
        return QueryOutputVector.builder().key(key).distance(distance).metadata(Document.fromMap(Map.of(
                "source", Document.fromString(key + ".md"),
                "headingPath", Document.fromList(List.of(Document.fromString("Guide"), Document.fromString("Section"))),
                "ordinal", Document.fromNumber(2), "source_text", Document.fromString("Evidence " + key + ".")))).build();
    }
}
