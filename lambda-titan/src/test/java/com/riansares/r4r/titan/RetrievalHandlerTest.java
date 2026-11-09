package com.riansares.r4r.titan;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.node.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import org.mockito.ArgumentCaptor;
import java.util.*;

class RetrievalHandlerTest {
    BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);
    S3VectorsClient vectors = mock(S3VectorsClient.class);
    RetrievalHandler handler = new RetrievalHandler(bedrock, vectors, null);
    ObjectNode request() { return RetrievalHandler.JSON.createObjectNode().put("question", "¿Qué datos necesita una reforma?"); }
    ObjectNode embedding() {
        var node = RetrievalHandler.JSON.createObjectNode(); var values = node.putArray("embedding");
        for (int i = 0; i < 1024; i++) values.add(i == 0 ? 1 : 0);
        return node;
    }
    Document metadata() { return Document.fromMap(Map.of("source", Document.fromString("test.md"),
        "source_text", Document.fromString("Texto original"), "ordinal", Document.fromNumber(3))); }
    void stubEmbedding() { when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenReturn(
        InvokeModelResponse.builder().body(SdkBytes.fromUtf8String(embedding().toString())).build()); }
    void stubHit() {
        when(vectors.queryVectors(any(QueryVectorsRequest.class))).thenReturn(QueryVectorsResponse.builder()
            .distanceMetric(DistanceMetric.COSINE).vectors(QueryOutputVector.builder().key("original-id")
                .distance(0.2f).metadata(metadata()).build()).build());
    }
    @Test void reconstructsOriginalCitationAndMeasuresStages() throws Exception {
        stubEmbedding(); stubHit(); var response = handler.retrieve(request());
        var docs = (List<?>) response.get("documents"); var doc = (Map<?,?>)docs.getFirst();
        assertEquals("original-id", doc.get("id")); assertEquals("test.md", doc.get("source"));
        assertEquals(3, doc.get("ordinal")); assertEquals(List.of(), doc.get("headingPath"));
        assertEquals("Texto original", doc.get("text")); assertEquals(0.8, (double)doc.get("score"), 1e-6);
        var metrics = (Map<?,?>)response.get("metrics");
        assertTrue((double)metrics.get("totalMs") >= (double)metrics.get("embeddingMs"));
        assertEquals("uncalibrated", response.get("calibration"));
    }
    @Test void sendsTitan1024NormalizedAndFloat32ToSeparateIndex() throws Exception {
        stubEmbedding(); stubHit(); handler.retrieve(request());
        var br = ArgumentCaptor.forClass(InvokeModelRequest.class); verify(bedrock).invokeModel(br.capture());
        var body = RetrievalHandler.JSON.readTree(br.getValue().body().asUtf8String());
        assertEquals(1024, body.get("dimensions").intValue()); assertTrue(body.get("normalize").booleanValue());
        assertEquals(RetrievalHandler.MODEL, br.getValue().modelId());
        var vr = ArgumentCaptor.forClass(QueryVectorsRequest.class); verify(vectors).queryVectors(vr.capture());
        assertEquals("r4r-titan-v2-1024", vr.getValue().indexName());
        assertEquals(1024, vr.getValue().queryVector().float32().size()); assertTrue(vr.getValue().returnMetadata());
    }
    @Test void emptySearchAbstains() throws Exception {
        stubEmbedding(); when(vectors.queryVectors(any(QueryVectorsRequest.class))).thenReturn(
            QueryVectorsResponse.builder().distanceMetric(DistanceMetric.COSINE).build());
        assertEquals(true, handler.retrieve(request()).get("abstained"));
    }
    @Test void configuredTitanThresholdCanAbstain() throws Exception {
        stubEmbedding(); stubHit(); assertEquals(true, new RetrievalHandler(bedrock,vectors,0.9).retrieve(request()).get("abstained"));
    }
    @Test void rejectsBadInputBeforeAws() {
        for (var input : List.of(request().put("topK",0), request().put("topK",1.5), request().put("question"," ")))
            assertThrows(IllegalArgumentException.class, () -> handler.retrieve(input));
        verifyNoInteractions(bedrock,vectors);
    }
    @Test void rejectsWrongDimensionsAndZeroVectors() {
        assertThrows(IllegalStateException.class, () -> RetrievalHandler.decodeEmbedding(RetrievalHandler.JSON.createObjectNode()));
        var node = embedding(); ((ArrayNode)node.get("embedding")).set(0,IntNode.valueOf(0));
        assertThrows(IllegalStateException.class, () -> RetrievalHandler.decodeEmbedding(node));
        ((ArrayNode)node.get("embedding")).set(0,TextNode.valueOf("bad"));
        assertThrows(IllegalStateException.class, () -> RetrievalHandler.decodeEmbedding(node));
    }
    @Test void bedrockDenialDoesNotQueryVectors() {
        when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenThrow(AccessDeniedException.builder().message("private").build());
        assertThrows(AccessDeniedException.class, () -> handler.retrieve(request())); verifyNoInteractions(vectors);
    }
    @Test void vectorFailureIsNotEmptyResult() {
        stubEmbedding(); when(vectors.queryVectors(any(QueryVectorsRequest.class))).thenThrow(S3VectorsException.builder().statusCode(503).build());
        assertThrows(S3VectorsException.class, () -> handler.retrieve(request()));
    }
    @Test void embeddingTimeoutPropagates() {
        when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenThrow(ApiCallTimeoutException.builder().message("timeout").build());
        assertThrows(ApiCallTimeoutException.class, () -> handler.retrieve(request())); verifyNoInteractions(vectors);
    }
    @Test void vectorTimeoutPropagates() {
        stubEmbedding(); when(vectors.queryVectors(any(QueryVectorsRequest.class))).thenThrow(ApiCallTimeoutException.builder().message("timeout").build());
        assertThrows(ApiCallTimeoutException.class, () -> handler.retrieve(request()));
    }
    @Test void rejectsBrokenProvenance() {
        assertThrows(IllegalStateException.class, () -> RetrievalHandler.document("id",0.5,Document.fromMap(Map.of())));
    }
    @Test void streamErrorsAreSanitizedAndNeverLogQuestion() throws Exception {
        var context = mock(com.amazonaws.services.lambda.runtime.Context.class);
        var log = mock(com.amazonaws.services.lambda.runtime.LambdaLogger.class);
        when(context.getLogger()).thenReturn(log); when(context.getAwsRequestId()).thenReturn("request-id");
        when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenThrow(AccessDeniedException.builder().message("sensitive-secret").build());
        var failure = assertThrows(java.io.IOException.class, () -> handler.handleRequest(
            new java.io.ByteArrayInputStream(request().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), new java.io.ByteArrayOutputStream(),context));
        assertEquals("Retrieval unavailable",failure.getMessage()); assertNull(failure.getCause());
        var messages = ArgumentCaptor.forClass(String.class); verify(log).log(messages.capture());
        assertFalse(messages.getValue().contains("sensitive-secret")); assertFalse(messages.getValue().contains("reforma"));
    }
}
