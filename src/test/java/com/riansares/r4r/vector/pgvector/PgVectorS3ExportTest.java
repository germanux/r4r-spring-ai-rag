package com.riansares.r4r.vector.pgvector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PgVectorS3ExportTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final VectorStore vectors = mock(VectorStore.class);
    private final PgVectorKnowledgeStore store = new PgVectorKnowledgeStore(vectors, jdbc);
    private Map<String,Object> row(String text, List<String> headings, int dimensions) throws Exception {
        float[] embedding = new float[dimensions]; embedding[0] = .5f;
        return Map.of("id", "00000000-0000-0000-0000-000000000001", "content", text,
                "metadata", json.writeValueAsString(Map.of("source", "en/guide.md", "headingPath", headings, "ordinal", 3)),
                "embedding", json.writeValueAsString(embedding));
    }
    @Test void exportsExistingFloat32DataAndMetadataWithoutCallingTheVectorStore() throws Exception {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(row("Full evidence\nwith whitespace.", List.of(), 1024)));
        Path out = directory.resolve("vectors.json");
        assertThat(store.exportS3Vectors(out)).isEqualTo(1);
        var record = json.readTree(out.toFile()).path("vectors").get(0);
        assertThat(record.path("data").path("float32").size()).isEqualTo(1024);
        assertThat(record.path("data").path("float32").get(0).asDouble()).isEqualTo(.5);
        assertThat(record.path("metadata").path("source_text").asText()).isEqualTo("Full evidence\nwith whitespace.");
        assertThat(record.path("metadata").has("headingPath")).isFalse();
        assertThat(record.path("metadata").path("ordinal").asInt()).isEqualTo(3);
        verifyNoInteractions(vectors);
    }
    @Test void reusesActualPgHeadingExpansionAndMarksItsContext() throws Exception {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(row("# Parent", List.of("Parent"), 1024)));
        when(jdbc.queryForList(contains("SELECT content"), eq(String.class), any(), any(), any(), any()))
                .thenReturn(List.of("## Child\nEvidence from the section."));
        Path out = directory.resolve("vectors.json");
        store.exportS3Vectors(out);
        var metadata = json.readTree(out.toFile()).path("vectors").get(0).path("metadata");
        assertThat(metadata.path("source_text").asText()).isEqualTo("# Parent\n\n## Child\nEvidence from the section.");
        assertThat(metadata.path("r4r_context").asText()).isEqualTo("pgvector-heading-v1");
        verifyNoInteractions(vectors);
    }
    @Test void exportsOriginalAndTripleWindowWithoutRegeneratingOrDroppingMetadata() throws Exception {
        var original = row("Original evidence", List.of("Guide"), 1024);
        var window = new HashMap<String, Object>(row("Original evidence\n\nAdjacent evidence", List.of("Guide"), 1024));
        window.put("id", "00000000-0000-0000-0000-000000000002");
        window.put("metadata", json.writeValueAsString(Map.of("source", "en/guide.md", "headingPath", List.of("Guide"),
                "ordinal", 1000000, "r4r_experiment", "triple-even-v1", "coveredOrdinals", List.of(0, 1))));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(original, window));
        var out = directory.resolve("both.json");
        assertThat(store.exportS3Vectors(out)).isEqualTo(2);
        var rows = json.readTree(out.toFile()).path("vectors");
        assertThat(rows.get(0).path("metadata").has("r4r_experiment")).isFalse();
        assertThat(rows.get(1).path("metadata").path("r4r_experiment").asText()).isEqualTo("triple-even-v1");
        assertThat(rows.get(1).path("metadata").path("coveredOrdinals").toString()).isEqualTo("[0,1]");
        assertThat(rows.get(1).path("metadata").path("ordinal").asInt()).isEqualTo(1000000);
        assertThat(rows.get(1).path("data")).isEqualTo(rows.get(0).path("data"));
        verifyNoInteractions(vectors);
    }

    @Test void refusesOldVectorsAndRemovesIncompleteExports() throws Exception {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(row("Old evidence", List.of(), 768)));
        assertThatThrownBy(() -> store.exportS3Vectors(directory.resolve("vectors.json"))).hasMessageContaining("1024");
        try (var files = Files.list(directory)) { assertThat(files.toList()).isEmpty(); }
    }
    @Test void refusesEmptyExportsAndNeverOverwritesAnExistingFile() throws Exception {
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        Path out = directory.resolve("vectors.json");
        assertThatThrownBy(() -> store.exportS3Vectors(out)).hasMessageContaining("empty");
        Files.writeString(out, "existing backup");
        assertThatThrownBy(() -> store.exportS3Vectors(out)).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(out)).isEqualTo("existing backup");
    }
}
