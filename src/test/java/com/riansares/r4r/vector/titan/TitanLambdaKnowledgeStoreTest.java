package com.riansares.r4r.vector.titan;

import com.riansares.r4r.rag.RagProgress;
import com.riansares.r4r.vector.VectorStoreUnavailableException;
import com.riansares.r4r.vector.config.TitanRetrievalProperties;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TitanLambdaKnowledgeStoreTest {
    final LambdaClient lambda = mock(LambdaClient.class);
    final TitanLambdaKnowledgeStore store = new TitanLambdaKnowledgeStore(lambda,
            new TitanRetrievalProperties(true, null, null, Duration.ofSeconds(2), 5, .6));
    private static com.fasterxml.jackson.databind.JsonNode parse(String value) {
        try { return TitanResponses.JSON.readTree(value); } catch (Exception failure) { throw new AssertionError(failure); }
    }
    @AfterEach void clearInterrupt() { Thread.interrupted(); }
    @Test void invokesTextOnlySynchronouslyAndPreservesCitationsAndSeparateMetrics() throws Exception {
        when(lambda.invoke(any(InvokeRequest.class))).thenReturn(TitanResponses.response(TitanResponses.body(true)));
        var progress = RagProgress.silent();
        var chunks = progress.duringRetrieval(() -> store.search("¿Qué reformas hacéis?", 7, .99));
        assertThat(chunks).hasSize(1); assertThat(chunks.getFirst().source()).isEqualTo("faq.md");
        assertThat(chunks.getFirst().headingPath()).containsExactly("Servicios"); assertThat(chunks.getFirst().index()).isEqualTo(2);
        verify(lambda).invoke(argThat((InvokeRequest request) -> {
            assertThat(request.invocationType()).isEqualTo(InvocationType.REQUEST_RESPONSE);
            assertThat(request.functionName()).isEqualTo("r4r-titan-retrieval");
            var body = parse(request.payload().asUtf8String());
            assertThat(body.path("question").asText()).isEqualTo("¿Qué reformas hacéis?");
            assertThat(body.path("topK").asInt()).isEqualTo(5);
            assertThat(body.size()).isEqualTo(2); return true;
        }));
        assertThat(progress.metrics()).containsEntry("embeddingMs", 3.0).containsEntry("vectorSearchMs", 2.0);
        assertThat((Double)progress.metrics().get("lambdaInvokeMs")).isGreaterThanOrEqualTo(0);
    }
    @Test void validEmptyOrBelowTitanThresholdReturnsNoEvidence() {
        var low = TitanResponses.body(true); ((com.fasterxml.jackson.databind.node.ObjectNode)low.at("/documents/0")).put("score", .2);
        when(lambda.invoke(any(InvokeRequest.class))).thenReturn(TitanResponses.response(TitanResponses.body(false)), TitanResponses.response(low));
        assertThat(store.search("q", 5, .1)).isEmpty(); assertThat(store.search("q", 5, .1)).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"AccessDeniedException", "Bedrock AccessDenied", "timeout"})
    void functionErrorsRemainControlledAndNeverLeakPayload(String message) {
        when(lambda.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder().statusCode(200).functionError("Unhandled")
                .payload(SdkBytes.fromUtf8String("private-secret " + message)).build());
        assertThatThrownBy(() -> store.search("q", 5, .6)).isInstanceOf(VectorStoreUnavailableException.class)
                .hasMessage("Titan retrieval unavailable").hasNoCause();
    }
    @Test void sdkTimeoutAndUnavailableLambdaAreNotSuccessfulEmptyResults() {
        when(lambda.invoke(any(InvokeRequest.class))).thenThrow(ApiCallTimeoutException.builder().message("private").build())
                .thenThrow(ResourceNotFoundException.builder().message("private").build());
        var progress = RagProgress.silent();
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> progress.duringRetrieval(() -> store.search("q", 5, .6)))
                .isInstanceOf(VectorStoreUnavailableException.class);
        assertThat((Double)progress.metrics().get("lambdaInvokeMs")).isGreaterThanOrEqualTo(0);
        assertThat(progress.metrics().get("embeddingMs")).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"space", "text", "ordinal", "metrics", "duplicate", "abstained", "score", "dimensions-text", "dimensions-fraction", "dimensions-nomic", "source-type"})
    void rejectsCorruptContract(String corruption) {
        var body = TitanResponses.body(true);
        var doc = (com.fasterxml.jackson.databind.node.ObjectNode) body.at("/documents/0");
        switch (corruption) {
            case "space" -> ((com.fasterxml.jackson.databind.node.ObjectNode)body.get("provider")).put("index", "amzn-r4r-vector-index");
            case "text" -> doc.put("text", "changed");
            case "ordinal" -> doc.put("ordinal", 2.5);
            case "metrics" -> ((com.fasterxml.jackson.databind.node.ObjectNode)body.get("metrics")).put("embeddingMs", -1);
            case "duplicate" -> ((com.fasterxml.jackson.databind.node.ArrayNode)body.get("documents")).add(doc.deepCopy());
            case "abstained" -> body.put("abstained", true);
            case "score" -> doc.put("score", 2);
            case "dimensions-text" -> ((com.fasterxml.jackson.databind.node.ObjectNode)body.get("provider")).put("dimensions", "1024");
            case "dimensions-fraction" -> ((com.fasterxml.jackson.databind.node.ObjectNode)body.get("provider")).put("dimensions", 1024.5);
            case "dimensions-nomic" -> ((com.fasterxml.jackson.databind.node.ObjectNode)body.get("provider")).put("dimensions", 768);
            case "source-type" -> {
                doc.put("source", 123); ((com.fasterxml.jackson.databind.node.ObjectNode)doc.get("metadata")).put("source", "123");
            }
        }
        when(lambda.invoke(any(InvokeRequest.class))).thenReturn(TitanResponses.response(body));
        assertThatThrownBy(() -> store.search("q", 5, .6)).isInstanceOf(VectorStoreUnavailableException.class);
    }
    @Test void sdkInterruptionKeepsCancellationAndThreadFlag() {
        when(lambda.invoke(any(InvokeRequest.class))).thenThrow(
                software.amazon.awssdk.core.exception.SdkClientException.create("private", new InterruptedException()));
        assertThatThrownBy(() -> store.search("q", 5, .6)).isInstanceOf(CancellationException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
    @Test void nestedSdkCancellationIsNotConvertedToServiceUnavailable() {
        when(lambda.invoke(any(InvokeRequest.class))).thenThrow(
                software.amazon.awssdk.core.exception.SdkClientException.create("private", new CancellationException()));
        assertThatThrownBy(() -> store.search("q", 5, .6)).isInstanceOf(CancellationException.class);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }
    @Test void rejectsInputAndCancellationBeforeAwsAndHasNoWritePath() {
        assertThatThrownBy(() -> store.search(" ", 5, .6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.index(java.util.List.of())).isInstanceOf(UnsupportedOperationException.class);
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> store.search("q", 5, .6)).isInstanceOf(CancellationException.class);
        verifyNoInteractions(lambda);
    }
}
