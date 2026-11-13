package com.riansares.r4r.vector;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.s3vectors.S3VectorMetadataMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import software.amazon.awssdk.core.document.Document;

/** Real immutable corpus/query vectors replayed offline. No generated substitute embeddings. */
final class NomicRegressionCorpus {
    static final ObjectMapper JSON = new ObjectMapper();
    record Row(String id, MarkdownChunk chunk, Map<String,Object> metadata, float[] vector) { }
    record Scored(Row row, double similarity) { }
    final List<Row> rows = new ArrayList<>();
    final JsonNode queries;
    NomicRegressionCorpus() throws Exception {
        try (var stream = getClass().getResourceAsStream("/retrieval/nomic-query-vectors.json")) { queries = JSON.readTree(stream); }
        var bytes = Files.readAllBytes(Path.of("docs/knowledge/data/struct-data/r4r-s3vectors-import.json"));
        if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(queries.path("corpusSha256").asText()))
            throw new IllegalStateException("Regression corpus checksum changed");
        for (var row : JSON.readTree(bytes).path("vectors")) {
            Map<String,Object> metadata = JSON.convertValue(row.path("metadata"), new TypeReference<>() {});
            rows.add(new Row(row.path("key").asText(), new S3VectorMetadataMapper().fromMetadata(metadata), metadata,
                    JSON.convertValue(row.path("data").path("float32"), float[].class)));
        }
    }
    float[] query(String key) { return JSON.convertValue(queries.path("vectors").path(key), float[].class); }
    List<Scored> ranked(float[] query) {
        return rows.stream().map(row -> new Scored(row, cosine(query, row.vector())))
                .sorted(Comparator.comparingDouble(Scored::similarity).reversed()).toList();
    }
    static double cosine(float[] a,float[] b) {
        double dot=0,aa=0,bb=0;
        for(int i=0;i<a.length;i++){dot+=(double)a[i]*b[i];aa+=(double)a[i]*a[i];bb+=(double)b[i]*b[i];}
        return dot/Math.sqrt(aa*bb);
    }
    static Document document(Object value) {
        if (value instanceof Map<?,?> map) {
            Map<String,Document> result=new LinkedHashMap<>();map.forEach((key,child)->result.put((String)key,document(child)));return Document.fromMap(result);
        }
        if(value instanceof List<?> list)return Document.fromList(list.stream().map(NomicRegressionCorpus::document).toList());
        if(value instanceof Number number)return Document.fromNumber(number.intValue());
        return Document.fromString((String)value);
    }
}
