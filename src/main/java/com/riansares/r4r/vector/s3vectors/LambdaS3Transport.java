package com.riansares.r4r.vector.s3vectors;

import com.fasterxml.jackson.databind.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;
import software.amazon.awssdk.services.s3vectors.model.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import java.util.*;
import java.util.function.Function;
import java.util.concurrent.CancellationException;

/** Wire transport only: relevance rules remain in S3VectorsKnowledgeStore. */
public final class LambdaS3Transport implements Function<QueryVectorsRequest,QueryVectorsResponse> {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final LambdaClient client; private final String function;
    public LambdaS3Transport(LambdaClient client,String function) {this.client=client;this.function=function;}
    public QueryVectorsResponse apply(QueryVectorsRequest request) {
        if(Thread.currentThread().isInterrupted()) throw new CancellationException();
        try {
            var response=client.invoke(InvokeRequest.builder().functionName(function).invocationType(InvocationType.REQUEST_RESPONSE)
                .payload(SdkBytes.fromByteArray(JSON.writeValueAsBytes(Map.of("schemaVersion",1,"embedding",request.queryVector().float32(),"topK",request.topK())))).build());
            if(Thread.currentThread().isInterrupted()) throw new CancellationException();
            if(response!=null && response.functionError()!=null) throw new com.riansares.r4r.vector.VectorStoreUnavailableException("S3 Lambda unavailable",null);
            if(response==null || !Integer.valueOf(200).equals(response.statusCode()) || response.functionError()!=null || response.payload()==null
                || response.payload().asByteArray().length>5*1024*1024) throw new IllegalStateException("Invalid Lambda response");
            var body=JSON.readTree(response.payload().asByteArray());
            if(!body.path("schemaVersion").isIntegralNumber() || !body.path("schemaVersion").canConvertToInt() || body.path("schemaVersion").intValue()!=1
                || !"amzn-r4r-vector-index".equals(body.path("index").asText()) || !"amzn-r4r-vectors".equals(body.path("bucket").asText())
                || !body.path("dimensions").isIntegralNumber() || !body.path("dimensions").canConvertToInt() || body.path("dimensions").intValue()!=768
                || !"cosine".equals(body.path("distanceMetric").asText()) || !body.path("vectors").isArray()
                || body.path("vectors").size()>request.topK() || !body.path("vectorSearchMs").isNumber()
                || !Double.isFinite(body.path("vectorSearchMs").doubleValue()) || body.path("vectorSearchMs").doubleValue()<0) throw new IllegalStateException("Invalid Lambda schema");
            List<QueryOutputVector> hits=new ArrayList<>();
            for(var hit:body.path("vectors")) {
                if(!hit.path("key").isTextual() || !hit.path("distance").isNumber() || !hit.path("metadata").isObject()) throw new IllegalStateException("Invalid Lambda hit");
                hits.add(QueryOutputVector.builder().key(hit.path("key").textValue()).distance(hit.path("distance").floatValue()).metadata(document(hit.path("metadata"))).build());
            }
            return QueryVectorsResponse.builder().distanceMetric(DistanceMetric.COSINE).vectors(hits).build();
        } catch(CancellationException failure) {throw failure;}
        catch(com.riansares.r4r.vector.VectorStoreUnavailableException failure) {throw failure;}
        catch(software.amazon.awssdk.core.exception.SdkException failure) {throw failure;}
        catch(Exception failure) {throw new IllegalStateException("Invalid S3 Lambda response");}
    }
    private static Document document(JsonNode node) {
        if(node.isObject()) {Map<String,Document> map=new LinkedHashMap<>();node.fields().forEachRemaining(e->map.put(e.getKey(),document(e.getValue())));return Document.fromMap(map);}
        if(node.isArray()) {List<Document> values=new ArrayList<>();node.forEach(v->values.add(document(v)));return Document.fromList(values);}
        if(node.isTextual()) return Document.fromString(node.textValue());
        if(node.isNumber()) return Document.fromNumber(node.decimalValue());
        if(node.isBoolean()) return Document.fromBoolean(node.booleanValue());
        return Document.fromNull();
    }
}
