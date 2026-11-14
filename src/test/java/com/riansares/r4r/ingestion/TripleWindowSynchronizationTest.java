package com.riansares.r4r.ingestion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Deterministic incremental contracts; no live embedding provider. */
class TripleWindowSynchronizationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EmbeddingModel embeddings = mock(EmbeddingModel.class);
    private final Map<String, String> stored = new HashMap<>();
    private final Map<String, String> metadata = new HashMap<>();
    private List<String> contents = List.of("first", "second", "third", "fourth");

    @AfterEach void resetTransaction() { TransactionSynchronizationManager.setActualTransactionActive(false); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setup() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("vector(1024)");
        when(jdbc.queryForList(anyString(), eq(String.class), eq("triple-even-v1")))
                .thenAnswer(call -> new ArrayList<>(stored.keySet()));
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(call -> {
            RowMapper mapper = call.getArgument(1);
            var chunks = new ArrayList<>();
            for (int i = 0; i < contents.size(); i++) {
                var rs = mock(ResultSet.class);
                when(rs.getString("source")).thenReturn("es/guide.md");
                when(rs.getInt("ordinal")).thenReturn(i);
                when(rs.getString("content")).thenReturn(contents.get(i));
                when(rs.getString("headings")).thenReturn("[\"Guide\"]");
                chunks.add(mapper.mapRow(rs, i));
            }
            return chunks;
        });
        when(embeddings.embed(anyString())).thenAnswer(call -> { float[] vector = new float[1024]; vector[0] = 1; return vector; });
        when(jdbc.update(startsWith("INSERT"), any(Object[].class))).thenAnswer(call -> {
            stored.put(call.getArgument(1), call.getArgument(2));
            metadata.put(call.getArgument(1), call.getArgument(3)); return 1;
        });
        when(jdbc.update(startsWith("UPDATE"), any(Object[].class))).thenAnswer(call -> {
            metadata.put(call.getArgument(2), call.getArgument(1)); return 1;
        });
        when(jdbc.queryForList(contains("vector_dims"), eq("triple-even-v1"))).thenAnswer(call ->
                stored.entrySet().stream().map(entry -> Map.<String, Object>of(
                        "id", entry.getKey(), "content", entry.getValue(), "metadata", metadata.get(entry.getKey()),
                        "dimensions", 1024, "norm", 1.0)).toList());
        when(jdbc.update(startsWith("DELETE"), any(Object[].class))).thenAnswer(call -> {
            assertThat(call.getArgument(2, String.class)).isEqualTo("triple-even-v1");
            return stored.remove(call.getArgument(1)) == null ? 0 : 1;
        });
    }

    @Test void repeatSkipsEmbeddingsAndEditOnlyReplacesAffectedWindow() {
        setup();
        var first = ExperimentalTripleWindowIndexer.synchronize(jdbc, embeddings);
        assertThat(first).containsEntry("added", 2).containsEntry("total", 2);
        var identities = Set.copyOf(stored.keySet());
        String legacyIdentity = "triple-even-v1\u0000es/guide.md\u00000\u0000first\n\nsecond";
        assertThat(identities).contains(UUID.nameUUIDFromBytes(legacyIdentity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
        assertThat(ExperimentalTripleWindowIndexer.synchronize(jdbc, embeddings))
                .containsEntry("added", 0).containsEntry("skipped", 2);
        verify(embeddings, times(2)).embed(anyString());
        contents = List.of("changed", "second", "third", "fourth");
        assertThat(ExperimentalTripleWindowIndexer.synchronize(jdbc, embeddings))
                .containsEntry("added", 1).containsEntry("removed", 1).containsEntry("skipped", 1);
        verify(embeddings, times(3)).embed(anyString());
        assertThat(stored.keySet().stream().filter(identities::contains)).hasSize(1);
        assertThat(stored.values()).contains("changed\n\nsecond", "second\n\nthird\n\nfourth");
        contents = List.of();
        assertThat(ExperimentalTripleWindowIndexer.synchronize(jdbc, embeddings)).containsEntry("removed", 2).containsEntry("total", 0);
        assertThat(stored).isEmpty();
    }

    @Test void corruptExistingContentCannotPassIntegrityChecks() {
        setup();
        ExperimentalTripleWindowIndexer.synchronize(jdbc, embeddings);
        stored.replaceAll((id, text) -> "corrupt text");
        assertThatThrownBy(() -> ExperimentalTripleWindowIndexer.synchronize(jdbc, embeddings))
                .hasMessageContaining("content/metadata/embedding mismatch");
        verify(embeddings, times(2)).embed(anyString());
    }

    @Test void requiresTransactionAndRejectsInvalidEmbeddingBeforeObsoleteDeletion() {
        assertThatThrownBy(() -> ExperimentalTripleWindowIndexer.synchronize(jdbc, embeddings)).hasMessageContaining("transaction");
        setup(); stored.put(UUID.randomUUID().toString(), "obsolete");
        when(embeddings.embed(anyString())).thenReturn(new float[1024]);
        assertThatThrownBy(() -> ExperimentalTripleWindowIndexer.synchronize(jdbc, embeddings)).hasMessageContaining("Zero embedding");
        verify(jdbc, never()).update(startsWith("DELETE"), any(Object[].class));
        assertThat(stored).hasSize(1);
    }
}
