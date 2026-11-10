package com.riansares.r4r.s3lambda;

import com.amazonaws.services.lambda.runtime.*;
import com.fasterxml.jackson.databind.*;
import com.riansares.r4r.vector.s3vectors.*;
import software.amazon.awssdk.services.s3vectors.*;
import software.amazon.awssdk.services.s3vectors.model.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import java.io.*;
import java.time.Duration;
import java.util.*;

public final class Handler implements RequestStreamHandler {
    static final ObjectMapper JSON = new ObjectMapper();
    private final S3VectorsClient client;
    private static final class Clients {
        static final S3VectorsClient CLIENT = S3VectorsClient.builder().region(Region.EU_NORTH_1)
            .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(1)).socketTimeout(Duration.ofSeconds(3)))
            .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(4)).apiCallAttemptTimeout(Duration.ofSeconds(3))
                .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())).build();
    }
    public Handler() { this(Clients.CLIENT); }
    public Handler(S3VectorsClient client) { this.client = client; }
    public Map<String,Object> query(JsonNode input) throws IOException {
        if (input == null || !input.isObject() || !input.path("schemaVersion").isIntegralNumber() || !input.path("schemaVersion").canConvertToInt() || input.path("schemaVersion").asInt() != 1
            || !input.path("topK").isIntegralNumber() || !input.path("topK").canConvertToInt()) throw new IllegalArgumentException();
        var fields=input.fieldNames();
        while(fields.hasNext()) if(!Set.of("schemaVersion","embedding","topK").contains(fields.next())) throw new IllegalArgumentException();
        int k = input.path("topK").intValue();
        if (k < 1 || k > 10000) throw new IllegalArgumentException();
        var embedding = input.path("embedding");
        if (!embedding.isArray() || embedding.size() != 768) throw new IllegalArgumentException();
        List<Float> values = new ArrayList<>(); double norm=0;
        for (var v:embedding) {
            if (!v.isNumber() || !Float.isFinite(v.floatValue())) throw new IllegalArgumentException();
            float f=v.floatValue(); values.add(f); norm+=(double)f*f;
        }
        if (norm==0) throw new IllegalArgumentException();
        var base=QueryVectorsRequest.builder().vectorBucketName("amzn-r4r-vectors").indexName("amzn-r4r-vector-index")
            .topK(k).returnDistance(true).returnMetadata(true).queryVector(VectorData.fromFloat32(values)).build();
        List<Map<String,Object>> hits=new ArrayList<>(); Set<String> keys=new HashSet<>(), tokens=new HashSet<>();
        String token=null; long start=System.nanoTime(); int pages=0;
        do {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            if (++pages>k+1) throw new IllegalStateException();
            var response=client.queryVectors(base.toBuilder().nextToken(token).build());
            if (response==null || response.distanceMetric()!=DistanceMetric.COSINE) throw new IllegalStateException();
            for(var hit:response.vectors()) {
                if(hit.key()==null || hit.key().isBlank() || !keys.add(hit.key()) || hit.distance()==null || hits.size()>=k) throw new IllegalStateException();
                new S3VectorScoreMapper().toScore(hit.distance());
                new S3VectorMetadataMapper().fromDocument(hit.metadata());
                hits.add(Map.of("key",hit.key(),"distance",hit.distance(),"metadata",javaValue(hit.metadata())));
            }
            token=response.nextToken(); if(token!=null && token.isBlank()) token=null;
            if(token!=null && !tokens.add(token)) throw new IllegalStateException();
        } while(token!=null);
        return Map.of("schemaVersion",1,"index","amzn-r4r-vector-index","bucket","amzn-r4r-vectors","dimensions",768,
            "distanceMetric","cosine","vectors",hits,"vectorSearchMs",(System.nanoTime()-start)/1e6);
    }
    // Document.toString is diagnostic output, not a JSON serialization contract.
    private static Object javaValue(software.amazon.awssdk.core.document.Document value) {
        if(value == null || value.isNull()) return null;
        if(value.isNumber()) return value.asNumber().bigDecimalValue();
        if(value.isString()) return value.asString();
        if(value.isBoolean()) return value.asBoolean();
        if(value.isList()) return value.asList().stream().map(Handler::javaValue).toList();
        if(value.isMap()) {
            Map<String,Object> map=new LinkedHashMap<>();
            value.asMap().forEach((key,child)->map.put(key,javaValue(child)));return map;
        }
        throw new IllegalStateException("Unsupported metadata");
    }
    @Override public void handleRequest(InputStream input,OutputStream output,Context context) throws IOException {
        try {
            byte[] bytes=input.readNBytes(65537); if(bytes.length>65536) throw new IllegalArgumentException();
            var result=query(JSON.readTree(bytes)); byte[] encoded=JSON.writeValueAsBytes(result);
            if(encoded.length>5*1024*1024) throw new IllegalStateException();
            output.write(encoded);
            context.getLogger().log(JSON.writeValueAsString(Map.of("event","s3_query","vectorSearchMs",result.get("vectorSearchMs")))+"\n");
        } catch(Exception failure) {
            context.getLogger().log("{\"event\":\"s3_query_failed\",\"errorType\":\""+failure.getClass().getSimpleName()+"\"}\n");
            throw new IOException("S3 retrieval failed");
        }
    }
}
