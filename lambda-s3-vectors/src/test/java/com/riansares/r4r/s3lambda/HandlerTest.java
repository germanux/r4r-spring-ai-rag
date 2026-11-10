package com.riansares.r4r.s3lambda;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import software.amazon.awssdk.services.s3vectors.*;
import software.amazon.awssdk.services.s3vectors.model.*;
import software.amazon.awssdk.core.document.Document;
import java.util.*;
class HandlerTest {
 S3VectorsClient client=mock(S3VectorsClient.class); Handler handler=new Handler(client);
 com.fasterxml.jackson.databind.node.ObjectNode input() {
  var n=Handler.JSON.createObjectNode().put("schemaVersion",1).put("topK",40);var a=n.putArray("embedding");
  for(int i=0;i<768;i++) a.add(i==0?1:0);return n;
 }
 QueryOutputVector hit() {return QueryOutputVector.builder().key("a").distance(.2f).metadata(Document.fromMap(Map.of(
 "source",Document.fromString("a.md"),"source_text",Document.fromString("Evidence"),"ordinal",Document.fromNumber(0)))).build();}
 QueryVectorsResponse page(String token) {return QueryVectorsResponse.builder().distanceMetric(DistanceMetric.COSINE).vectors(hit()).nextToken(token).build();}
 @Test void preservesMultilineAndQuotedMetadata() throws Exception {
  String text="Line one\n\"Quoted\" and backslash \\";
  var m=new java.util.LinkedHashMap<>(hit().metadata().asMap());m.put("source_text",Document.fromString(text));
  when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(page(null).toBuilder().vectors(hit().toBuilder().metadata(Document.fromMap(m)).build()).build());
  var result=Handler.JSON.readTree(Handler.JSON.writeValueAsBytes(handler.query(input())));
  assertEquals(text,result.at("/vectors/0/metadata/source_text").textValue());
  assertTrue(result.at("/vectors/0/metadata/ordinal").isIntegralNumber());
 }
 @Test void valid() throws Exception {when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(page(null));assertEquals(768,handler.query(input()).get("dimensions"));}
 @Test void invalidVectors() {var n=input();n.putArray("embedding").add(1);assertThrows(IllegalArgumentException.class,()->handler.query(n));n.set("embedding",Handler.JSON.nullNode());assertThrows(IllegalArgumentException.class,()->handler.query(n));verifyNoInteractions(client);}
 @Test void zeroAndNonfinite() {for(double v:new double[]{0,Double.NaN,Double.POSITIVE_INFINITY}) {var n=input();((com.fasterxml.jackson.databind.node.ArrayNode)n.get("embedding")).set(0,Handler.JSON.getNodeFactory().numberNode(v));assertThrows(IllegalArgumentException.class,()->handler.query(n));}}
 @Test void duplicateAndPagination() {when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(page("next"),page("next"));assertThrows(IllegalStateException.class,()->handler.query(input()));}
 @Test void excessive() {when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(QueryVectorsResponse.builder().distanceMetric(DistanceMetric.COSINE).vectors(hit(),hit()).build());assertThrows(IllegalStateException.class,()->handler.query(input().put("topK",1)));}
 @Test void badMetadata() {when(client.queryVectors(any(QueryVectorsRequest.class))).thenReturn(QueryVectorsResponse.builder().distanceMetric(DistanceMetric.COSINE).vectors(hit().toBuilder().metadata(Document.fromMap(Map.of())).build()).build());assertThrows(IllegalStateException.class,()->handler.query(input()));}
 @Test void awsDenial() {when(client.queryVectors(any(QueryVectorsRequest.class))).thenThrow(AccessDeniedException.builder().build());assertThrows(AccessDeniedException.class,()->handler.query(input()));}
 @Test void cancellation() {Thread.currentThread().interrupt();try{assertThrows(java.util.concurrent.CancellationException.class,()->handler.query(input()));}finally{Thread.interrupted();}}
}
