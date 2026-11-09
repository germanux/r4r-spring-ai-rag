package com.riansares.r4r.validation;

import java.sql.DriverManager;
import java.util.*;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;

/** Operator-only read evidence: never writes SQL/vectors or emits credentials. */
public final class LiveEvidenceSnapshot {
    public static void main(String[] args) throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();
        try {
            var env=new StandardEnvironment();
            env.getPropertySources().addFirst(new MapPropertySource("validation",Map.of("spring.config.location",
                    "file:src/main/resources/application.yml","spring.profiles.active","default")));
            ConfigDataEnvironmentPostProcessor.applyTo(env);
            if (args[0].equals("pg") || args[0].equals("compat") || args[0].equals("retrieval")) {
                try (var connection=DriverManager.getConnection("jdbc:postgresql://192.168.1.203:5432/r4r_rag?connectTimeout=3&socketTimeout=10",
                        env.getRequiredProperty("POSTGRES_USER"),env.getRequiredProperty("POSTGRES_PASSWORD"))) {
                    connection.setReadOnly(true); connection.setAutoCommit(false);
                    try (var statement=connection.createStatement()) {
                        var mode=statement.executeQuery("SHOW transaction_read_only");mode.next();
                        if (!mode.getString(1).equals("on")) throw new IllegalStateException("Read-only check failed");
                        result.put("readOnly",true);
                        if (args[0].equals("retrieval")) {
                            var api=org.springframework.ai.ollama.api.OllamaApi.builder().baseUrl("http://192.168.1.209:11434").build();
                            var embeddings=new com.riansares.r4r.rag.ObservedEmbeddingModel(api,
                                    org.springframework.ai.ollama.api.OllamaOptions.builder().model(env.getRequiredProperty("RAG_EMBEDDING_MODEL")).build());
                            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
                            var vectors=org.springframework.ai.vectorstore.pgvector.PgVectorStore.builder(jdbc,embeddings)
                                    .dimensions(768).initializeSchema(false).build();
                            var store=new com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore(vectors,jdbc);
                            var chunks=store.search(args[1],5,.63);
                            result.put("retrieved",chunks.stream().map(chunk -> Map.of("source",chunk.source(),
                                    "headingPath",chunk.headingPath(),"ordinal",chunk.index(),
                                    "containsOffice",java.util.regex.Pattern.compile("(?iu)\\b(oficinas?|despachos?)\\b").matcher(chunk.content()).find())).toList());
                        }
                        if (args[0].equals("compat")) {
                            var stored=statement.executeQuery("SELECT id::text,content,embedding::text FROM vector_store ORDER BY id LIMIT 1");
                            stored.next(); result.put("storedVectorId",stored.getString(1)); String content=stored.getString(2);
                            double[] reference=new com.fasterxml.jackson.databind.ObjectMapper().readValue(stored.getString(3),double[].class);
                            for (String host:args.length>1 ? List.of(args[1]) : List.of("192.168.1.207","192.168.1.203")) {
                                var api=org.springframework.ai.ollama.api.OllamaApi.builder().baseUrl("http://"+host+":11434").build();
                                String model=env.getRequiredProperty("RAG_EMBEDDING_MODEL");
                                var installed=api.listModels().models().stream().filter(m -> m.name().equals(model)).findFirst().orElseThrow();
                                if (!installed.digest().equals(env.getProperty("RAG_EMBEDDING_EXPECTED_DIGEST"))) throw new IllegalStateException("Digest mismatch");
                                var embeddings=new com.riansares.r4r.rag.ObservedEmbeddingModel(api,
                                        org.springframework.ai.ollama.api.OllamaOptions.builder().model(model).build());
                                long started=System.nanoTime();float[] vector=embeddings.embed(content);
                                if (vector.length!=reference.length) throw new IllegalStateException("Dimension mismatch");
                                double dot=0,left=0,right=0,max=0;
                                for (int i=0;i<vector.length;i++) { dot+=vector[i]*reference[i];left+=vector[i]*vector[i];right+=reference[i]*reference[i];max=Math.max(max,Math.abs(vector[i]-reference[i])); }
                                double cosine=dot/Math.sqrt(left*right);
                                result.put(host,Map.of("digest",installed.digest(),"dimensions",vector.length,"cosineAgainstStored",cosine,
                                        "maxAbsDifference",max,"elapsedMs",(System.nanoTime()-started)/1_000_000.0));
                            }
                        }
                        var rows=statement.executeQuery("SELECT count(*), md5(string_agg(id::text || coalesce(content,'') || metadata::text || embedding::text, '|' ORDER BY id)) FROM vector_store");
                        rows.next();result.put("count",rows.getLong(1));result.put("fingerprint",rows.getString(2));
                    }
                }
            } else {
                try (var client=S3VectorsClient.builder().region(software.amazon.awssdk.regions.Region.of(env.getRequiredProperty("RAG_S3_VECTORS_REGION")))
                        .overrideConfiguration(c -> c.apiCallTimeout(java.time.Duration.ofSeconds(8))).build()) {
                    String bucket=env.getRequiredProperty("RAG_S3_VECTORS_BUCKET"), index=env.getRequiredProperty("RAG_S3_VECTORS_INDEX");
                    var info=client.getIndex(GetIndexRequest.builder().vectorBucketName(bucket).indexName(index).build()).index();
                    result.put("dimensions",info.dimension()); result.put("distance",info.distanceMetricAsString());
                    List<String> keys=new ArrayList<>();String token=null;
                    do {
                        var page=client.listVectors(ListVectorsRequest.builder().vectorBucketName(bucket).indexName(index).maxResults(100).nextToken(token).build());
                        for (var vector:page.vectors()) keys.add(vector.key());token=page.nextToken();
                        if (keys.size()>10000) throw new IllegalStateException("Inventory bound exceeded");
                    } while(token!=null);
                    Collections.sort(keys);result.put("count",keys.size());
                    result.put("keyFingerprint",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(String.join("\n",keys).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                }
            }
            result.put("status","PASS");
        } catch (Throwable failure) {
            result.clear();result.put("status","FAIL");result.put("type",failure.getClass().getSimpleName());
            if (failure instanceof software.amazon.awssdk.awscore.exception.AwsServiceException aws) {
                result.put("http",aws.statusCode());result.put("code",aws.awsErrorDetails().errorCode());
            }
        }
        System.out.println(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result));
        if (!"PASS".equals(result.get("status"))) System.exit(1);
    }
}
