package com.riansares.r4r.titan;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorpusImporterTest {
    final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);
    final S3VectorsClient vectors = mock(S3VectorsClient.class);
    final CorpusImporter importer = new CorpusImporter(vectors, bedrock);
    final Map<String, PutInputVector> stored = new LinkedHashMap<>();
    final Map<String, Object> metadata = new LinkedHashMap<>(Map.of("source", "test.md", "source_text", " Original\n", "ordinal", 2,
            "headingPath", List.of(), "r4r_context", "pgvector-heading-v1"));
    @TempDir Path directory;
    @BeforeEach void setup() throws Exception {
        when(vectors.getIndex(any(GetIndexRequest.class))).thenReturn(GetIndexResponse.builder().index(Index.builder()
                .dimension(1024).dataType(DataType.FLOAT32).distanceMetric(DistanceMetric.COSINE)
                .metadataConfiguration(MetadataConfiguration.builder().nonFilterableMetadataKeys("source_text").build()).build()).build());
        when(vectors.listVectors(any(ListVectorsRequest.class))).thenAnswer(call -> ListVectorsResponse.builder().vectors(
                stored.values().stream().map(row -> ListOutputVector.builder().key(row.key()).metadata(row.metadata()).build()).toList()).build());
        when(vectors.putVectors(any(PutVectorsRequest.class))).thenAnswer(call -> {
            PutVectorsRequest request = call.getArgument(0);
            assertEquals("amzn-r4r-vectors", request.vectorBucketName()); assertEquals("r4r-titan-v2-1024", request.indexName());
            request.vectors().forEach(row -> stored.put(row.key(), row)); return PutVectorsResponse.builder().build();
        });
        when(vectors.getVectors(any(GetVectorsRequest.class))).thenAnswer(call -> {
            GetVectorsRequest request = call.getArgument(0);
            return GetVectorsResponse.builder().vectors(request.keys().stream().filter(stored::containsKey).map(stored::get)
                    .map(row -> GetOutputVector.builder().key(row.key()).metadata(row.metadata()).data(row.data()).build()).toList()).build();
        });
        var values = CorpusImporter.JSON.createObjectNode().putArray("embedding");
        for (int i = 0; i < 1024; i++) values.add(i == 0 ? .5f : 0);
        when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenReturn(InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String("{\"embedding\":" + values + "}")).build());
    }
    Map<String, Map<String, Object>> records() { return Map.of("original-key", CorpusImporter.METADATA.forStorage(metadata)); }
    @Test void originalExportHas282UniqueUnchangedRecordsAndSameManifestAsPreviousTool() throws Exception {
        var rows = CorpusImporter.read(Path.of("../knowledge/struct-data/r4r-s3vectors-import.json"));
        assertEquals(282, rows.size());
        assertEquals("034703cf21bad0d54c6c4d246ad473188ef9138c6de2d537c0a17de44e0b3ed6", CorpusImporter.fingerprint(rows));
        verifyNoInteractions(bedrock, vectors);
    }
    @Test void importsAndSecondRunVerifiesRemoteVectorsWithoutEmbeddingAgain() throws Exception {
        var reportPath = directory.resolve("report.json");
        var first = importer.ingest(records(), report -> CorpusImporter.save(reportPath, report));
        assertTrue(first.success); assertEquals(1, first.imported);
        var second = importer.ingest(records(), report -> CorpusImporter.save(reportPath, report));
        assertTrue(second.success); assertEquals(1, second.resumed); assertEquals(0, second.imported);
        verify(bedrock, times(1)).invokeModel(any(InvokeModelRequest.class)); verify(vectors, times(1)).putVectors(any(PutVectorsRequest.class));
        var actual = CorpusImporter.METADATA.metadataValues(stored.get("original-key").metadata());
        assertEquals(" Original\n", actual.get("source_text")); assertFalse(actual.containsKey("headingPath"));
        assertTrue(CorpusImporter.JSON.readTree(reportPath.toFile()).path("success").booleanValue());
    }
    @Test void interruptedAfterWriteResumesFromRemoteRow() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> importer.ingest(records(), report -> {
            if (saves.incrementAndGet() == 2) throw new IllegalStateException("simulated local interruption");
        }));
        var resumed = importer.ingest(records(), report -> {}); assertTrue(resumed.success); assertEquals(1, resumed.resumed);
        verify(bedrock, times(1)).invokeModel(any(InvokeModelRequest.class));
    }
    @Test void denialStopsWithoutAnyWriteAndSavesSanitizedFailure() throws Exception {
        when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenThrow(AccessDeniedException.builder().message("private-secret").build());
        var path = directory.resolve("report.json");
        assertThrows(AccessDeniedException.class, () -> importer.ingest(records(), report -> CorpusImporter.save(path, report)));
        verify(vectors, never()).putVectors(any(PutVectorsRequest.class));
        var report = Files.readString(path); assertTrue(report.contains("AccessDeniedException")); assertFalse(report.contains("private-secret"));
        assertEquals(1, CorpusImporter.JSON.readTree(report).path("errors").intValue());
    }
    @Test void rejectsWrongIndexAndUnexpectedRemoteKeysBeforeEmbedding() throws Exception {
        stored.put("foreign-key", PutInputVector.builder().key("foreign-key").metadata(CorpusImporter.document(metadata)).build());
        assertThrows(IllegalStateException.class, () -> importer.ingest(records(), report -> {})); verifyNoInteractions(bedrock);
        stored.clear();
        when(vectors.getIndex(any(GetIndexRequest.class))).thenReturn(GetIndexResponse.builder().index(Index.builder()
                .dimension(768).dataType(DataType.FLOAT32).distanceMetric(DistanceMetric.COSINE).build()).build());
        assertThrows(IllegalStateException.class, () -> importer.ingest(records(), report -> {})); verifyNoInteractions(bedrock);
    }
    @Test void resumeRejectsCorruptVectorDespiteMatchingMetadata() throws Exception {
        stored.put("original-key", PutInputVector.builder().key("original-key").metadata(CorpusImporter.document(CorpusImporter.tagged(records().get("original-key"))))
                .data(VectorData.fromFloat32(Collections.nCopies(768, 1f))).build());
        assertThrows(IllegalStateException.class, () -> importer.ingest(records(), report -> {})); verifyNoInteractions(bedrock);
    }
    @Test void malformedEmbeddingsNeverReachStorage() {
        when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenReturn(InvokeModelResponse.builder().body(SdkBytes.fromUtf8String("{\"embedding\":[1]}" )).build());
        assertThrows(IllegalStateException.class, () -> importer.ingest(records(), report -> {})); verify(vectors, never()).putVectors(any(PutVectorsRequest.class));
    }
    @Test void metadataUsesExistingValidatorAndPreservesCompatibleFields() {
        var normalized = CorpusImporter.METADATA.forStorage(metadata);
        assertEquals(" Original\n", normalized.get("source_text")); assertEquals("pgvector-heading-v1", normalized.get("r4r_context"));
        assertFalse(normalized.containsKey("headingPath"));
        assertThrows(IllegalStateException.class, () -> CorpusImporter.METADATA.forStorage(Map.of()));
        var bad = new LinkedHashMap<>(metadata); bad.put("headingPath", List.of(4));
        assertThrows(IllegalStateException.class, () -> CorpusImporter.METADATA.forStorage(bad));
    }
    @Test void alteredExportIsRejectedOffline() throws Exception {
        var path = directory.resolve("changed.json"); Files.writeString(path, "{\"vectors\":[]}");
        assertThrows(IllegalStateException.class, () -> CorpusImporter.read(path)); verifyNoInteractions(bedrock, vectors);
    }
}
