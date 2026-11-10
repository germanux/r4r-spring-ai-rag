package com.riansares.r4r.titan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.riansares.r4r.vector.s3vectors.S3VectorMetadataMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import software.amazon.awssdk.services.sts.StsClient;

/** Regenerates embeddings of the immutable export, never re-chunks or reads PostgreSQL/Nomic. */
public final class CorpusImporter {
    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    static final S3VectorMetadataMapper METADATA = new S3VectorMetadataMapper();
    public static final String EXPORT_SHA256 = "89ab0be789a5399b58697d477169b577d21d11558cac13400d70d2f590e5c634";
    private final S3VectorsClient vectors;
    private final TitanEmbeddingClient embeddings;
    CorpusImporter(S3VectorsClient vectors, BedrockRuntimeClient bedrock) {
        this.vectors = vectors; this.embeddings = new TitanEmbeddingClient(bedrock);
    }
    static LinkedHashMap<String, Map<String, Object>> read(Path source) throws Exception {
        byte[] bytes = Files.readAllBytes(source);
        if (!EXPORT_SHA256.equals(sha(bytes))) throw new IllegalStateException("Unexpected corpus export checksum");
        var rows = JSON.readTree(bytes).path("vectors");
        if (!rows.isArray() || rows.size() != 282) throw new IllegalStateException("Expected the original 282 records");
        var records = new LinkedHashMap<String, Map<String, Object>>();
        for (var row : rows) {
            if (!row.path("key").isTextual() || row.path("key").asText().isBlank() || !row.path("metadata").isObject())
                throw new IllegalStateException("Invalid corpus record");
            Map<String, Object> original = JSON.convertValue(row.path("metadata"), new TypeReference<>() {});
            var normalized = METADATA.forStorage(original);
            if (records.putIfAbsent(row.path("key").asText(), normalized) != null) throw new IllegalStateException("Duplicate corpus key");
        }
        return records;
    }
    static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    static String fingerprint(Object value) throws Exception { return sha(JSON.writeValueAsBytes(value)); }
    static Map<String, Object> tagged(Map<String, Object> metadata) throws Exception {
        var result = new LinkedHashMap<>(metadata);
        result.put("embedding_model", RetrievalHandler.MODEL);
        result.put("titan_content_sha256", fingerprint(metadata));
        return METADATA.forStorage(result);
    }
    void validateIndex() {
        var index = vectors.getIndex(GetIndexRequest.builder().vectorBucketName(RetrievalHandler.BUCKET)
                .indexName(RetrievalHandler.INDEX).build()).index();
        if (index.dimension() != 1024 || index.dataType() != DataType.FLOAT32 || index.distanceMetric() != DistanceMetric.COSINE
                || index.metadataConfiguration() == null
                || !index.metadataConfiguration().nonFilterableMetadataKeys().equals(List.of("source_text")))
            throw new IllegalStateException("Unexpected target index");
    }
    Map<String, Map<String, Object>> remote() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Set<String> tokens = new HashSet<>(); String token = null;
        do {
            var page = vectors.listVectors(ListVectorsRequest.builder().vectorBucketName(RetrievalHandler.BUCKET)
                    .indexName(RetrievalHandler.INDEX).returnMetadata(true).nextToken(token).build());
            for (var row : page.vectors()) {
                if (row.key() == null || result.putIfAbsent(row.key(), METADATA.metadataValues(row.metadata())) != null || result.size() > 282)
                    throw new IllegalStateException("Invalid remote keys");
            }
            token = page.nextToken(); if (token != null && token.isBlank()) token = null;
            if (token != null && (!tokens.add(token) || tokens.size() > 282)) throw new IllegalStateException("Invalid pagination");
        } while (token != null);
        return result;
    }
    void verify(String key, Map<String, Object> metadata, List<Float> expected) {
        var rows = vectors.getVectors(GetVectorsRequest.builder().vectorBucketName(RetrievalHandler.BUCKET)
                .indexName(RetrievalHandler.INDEX).keys(key).returnData(true).returnMetadata(true).build()).vectors();
        if (rows.size() != 1 || !key.equals(rows.getFirst().key())
                || !sameMetadata(metadata, METADATA.metadataValues(rows.getFirst().metadata())))
            throw new IllegalStateException("Stored provenance mismatch");
        var actual = rows.getFirst().data().float32();
        TitanEmbeddingClient.decode(JSON.createObjectNode().set("embedding", JSON.valueToTree(actual)));
        if (expected != null && !expected.equals(actual)) throw new IllegalStateException("Stored vector mismatch");
    }
    Report ingest(Map<String, Map<String, Object>> records, Consumer<Report> checkpoint) throws Exception {
        var report = new Report(fingerprint(records)); checkpoint.accept(report);
        try {
            validateIndex();
            var ready = remote();
            if (!records.keySet().containsAll(ready.keySet())) throw new IllegalStateException("Unexpected remote keys; no deletion allowed");
            for (var entry : records.entrySet()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                report.currentKey = entry.getKey();
                var metadata = tagged(entry.getValue());
                boolean complete = sameMetadata(metadata, ready.get(entry.getKey()));
                if (complete) {
                    verify(entry.getKey(), metadata, null); report.resumed++;
                } else {
                    var data = embeddings.embed((String) metadata.get("source_text"));
                    vectors.putVectors(PutVectorsRequest.builder().vectorBucketName(RetrievalHandler.BUCKET)
                            .indexName(RetrievalHandler.INDEX).vectors(PutInputVector.builder().key(entry.getKey())
                                    .metadata(document(metadata)).data(VectorData.fromFloat32(data)).build()).build());
                    verify(entry.getKey(), metadata, data); report.imported++;
                }
                checkpoint.accept(report);
            }
            var finalRows = remote();
            if (!finalRows.keySet().equals(records.keySet())) throw new IllegalStateException("Final corpus keys mismatch");
            for (var entry : records.entrySet())
                if (!sameMetadata(tagged(entry.getValue()), finalRows.get(entry.getKey())))
                    throw new IllegalStateException("Final corpus metadata mismatch");
            report.success = true; report.currentKey = null; checkpoint.accept(report); return report;
        } catch (Exception failure) {
            report.errors++; report.errorType = failure.getClass().getSimpleName(); checkpoint.accept(report);
            throw failure; // Stop on first error, including throttling/denial. Remote rows are the durable checkpoint.
        }
    }
    static boolean sameMetadata(Object expected, Object actual) {
        try { return JSON.readTree(JSON.writeValueAsBytes(expected)).equals(JSON.readTree(JSON.writeValueAsBytes(actual))); }
        catch (Exception failure) { throw new IllegalStateException("Invalid metadata JSON"); }
    }
    static Document document(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Document>(); map.forEach((key, child) -> result.put((String)key, document(child)));
            return Document.fromMap(result);
        }
        if (value instanceof List<?> list) return Document.fromList(list.stream().map(CorpusImporter::document).toList());
        if (value instanceof String text) return Document.fromString(text);
        if (value instanceof Boolean bool) return Document.fromBoolean(bool);
        if (value instanceof Number number) return Document.fromNumber(new java.math.BigDecimal(number.toString()));
        throw new IllegalStateException("Unsupported metadata");
    }
    static final class Report {
        public final String manifestSha256;
        public int imported, resumed, errors;
        public String currentKey, errorType;
        public boolean success;
        Report(String manifest) { this.manifestSha256 = manifest; }
    }
    static void save(Path path, Report report) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            var temporary = Files.createTempFile(path.toAbsolutePath().getParent(), ".titan-report-", ".json");
            try {
                JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), report);
                try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        } catch (Exception failure) { throw new IllegalStateException("Cannot save import report"); }
    }
    public static void main(String[] args) {
        try { run(args); }
        catch (Exception failure) { System.err.println("Titan import failed: " + failure.getClass().getSimpleName()); System.exit(1); }
    }
    static void run(String[] args) throws Exception {
        Path source = Path.of("knowledge/struct-data/r4r-s3vectors-import.json");
        Path report = Path.of("lambda-titan/target/import-report.json"); boolean ingest = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--source" -> source = Path.of(args[++i]);
                case "--report" -> report = Path.of(args[++i]);
                case "--ingest" -> ingest = true;
                default -> throw new IllegalArgumentException("Unknown importer argument");
            }
        }
        var records = read(source);
        System.out.println(JSON.writeValueAsString(Map.of("documents", records.size(), "manifestSha256", fingerprint(records), "ingest", ingest)));
        if (!ingest) return; // Offline audit does not construct AWS clients or resolve credentials.
        var credentials = ProfileCredentialsProvider.builder().profileName("r4r-codex").build();
        var config = ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(20))
                .apiCallAttemptTimeout(Duration.ofSeconds(15)).retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()).build();
        try (credentials;
             var sts = StsClient.builder().credentialsProvider(credentials).region(Region.EU_NORTH_1)
                     .httpClientBuilder(UrlConnectionHttpClient.builder()).overrideConfiguration(config).build();
             var bedrock = BedrockRuntimeClient.builder().credentialsProvider(credentials).region(Region.EU_NORTH_1)
                     .httpClientBuilder(UrlConnectionHttpClient.builder()).overrideConfiguration(config).build();
             var vectors = S3VectorsClient.builder().credentialsProvider(credentials).region(Region.EU_NORTH_1)
                     .httpClientBuilder(UrlConnectionHttpClient.builder()).overrideConfiguration(config).build()) {
            var identity = sts.getCallerIdentity();
            if (!"124021120482".equals(identity.account())
                    || !"arn:aws:iam::124021120482:user/r4r-codex-deployer".equals(identity.arn()))
                throw new IllegalStateException("Unexpected importer identity");
            Path destination = report;
            var result = new CorpusImporter(vectors, bedrock).ingest(records, state -> save(destination, state));
            System.out.println(JSON.writeValueAsString(result));
        }
    }
}
