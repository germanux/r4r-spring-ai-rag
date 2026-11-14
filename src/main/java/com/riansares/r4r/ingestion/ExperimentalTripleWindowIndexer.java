package com.riansares.r4r.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.R4rSpringAiRagApplication;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * One-off additive PGVector experiment. No changes to the original 1,375 chunks/vectors.
 *
 * Each even, zero-based ordinal is a centre; the new embedding uses its previous,
 * current and next chunks, but never crosses a source/document boundary.
 *
 * Modes: --dry-run (default), --apply, --rollback.
 * Only --apply/--rollback modify PostgreSQL. S3 Vectors is never touched.
 * Experimental IDs are tracked directly by a metadata tag; no extra table is needed.
 */
public final class ExperimentalTripleWindowIndexer {
    private static final String EXPERIMENT = "triple-even-v1";
    private static final int DIMENSIONS = 1024;
    private static final int EXPERIMENT_ORDINAL_OFFSET = 1_000_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Chunk(String source, int ordinal, String content, List<String> headings) { }
    private record Window(UUID id, String source, int ordinal, String content,
                          List<String> headings, List<Integer> coveredOrdinals) { }

    private ExperimentalTripleWindowIndexer() { }

    public static void main(String[] args) {
        String mode = args.length == 0 ? "--dry-run" : args[0];
        if (args.length > 1 || !Set.of("--dry-run", "--apply", "--rollback").contains(mode)) {
            System.err.println("Usage: ExperimentalTripleWindowIndexer [--dry-run|--apply|--rollback]");
            System.exit(2);
        }
        // Same Spring AI embedding router and PostgreSQL datasource as the real ingestion CLI.
        try (var context = new SpringApplicationBuilder(R4rSpringAiRagApplication.class)
                .web(WebApplicationType.NONE)
                .run()) {
            var jdbc = context.getBean(JdbcTemplate.class);

            if ("--rollback".equals(mode)) {
                rollback(jdbc);
                return;
            }

            var windows = generateWindows(readOriginalChunks(jdbc));
            System.out.printf("Experiment=%s  candidate_windows=%d  mode=%s%n",
                    EXPERIMENT, windows.size(), mode);
            windows.stream().limit(5).forEach(w ->
                    System.out.printf("PREVIEW source=%s centre=%d includes=%s chars=%d%n",
                            w.source(), w.ordinal(), w.coveredOrdinals(), w.content().length()));
            if ("--dry-run".equals(mode)) return;

            verifyDimensions(jdbc);
            var completed = new HashSet<>(jdbc.queryForList("""
                    SELECT id::text FROM vector_store
                    WHERE metadata->>'r4r_experiment' = ?
                    """, String.class, EXPERIMENT));
            EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
            int added = 0, skipped = 0;
            for (Window window : windows) {
                if (completed.contains(window.id().toString())) {
                    skipped++;
                    continue;
                }
                float[] vector = embeddingModel.embed(window.content()); // Document: NO retrieval-query prefix.
                if (vector == null || vector.length != DIMENSIONS) {
                    throw new IllegalStateException("Expected Qwen3 vector of " + DIMENSIONS + " dimensions");
                }
                double normSquared = 0;
                for (float value : vector) {
                    if (!Float.isFinite(value)) throw new IllegalStateException("Non-finite vector");
                    normSquared += (double) value * value;
                }
                if (normSquared == 0) throw new IllegalStateException("Zero embedding");

                String metadata = json(Map.of(
                        "source", window.source(),
                        "headingPath", window.headings(),
                        "ordinal", EXPERIMENT_ORDINAL_OFFSET + window.ordinal(),
                        "r4r_experiment", EXPERIMENT,
                        "coveredOrdinals", window.coveredOrdinals()));
                String pgVector = toPgVector(vector);
                int inserted = jdbc.update("""
                        INSERT INTO vector_store(id, content, metadata, embedding)
                        VALUES (?::uuid, ?, ?::jsonb, ?::vector)
                        ON CONFLICT (id) DO NOTHING
                        """, window.id().toString(), window.content(), metadata, pgVector);
                if (inserted != 1) {
                    throw new IllegalStateException("Unexpected vector ID conflict: " + window.id());
                }
                added++;
                if (added % 25 == 0) System.out.printf("Progress: added=%d skipped=%d%n", added, skipped);
            }
            System.out.printf("DONE added=%d skipped=%d candidate_windows=%d%n",
                    added, skipped, windows.size());
        }
    }

    private static List<Chunk> readOriginalChunks(JdbcTemplate jdbc) {
        return jdbc.query("""
                SELECT s.source_path AS source, c.ordinal AS ordinal,
                       c.content AS content, array_to_json(c.heading_path)::text AS headings
                FROM knowledge_chunks c JOIN knowledge_sources s ON s.id = c.source_id
                ORDER BY s.source_path, c.ordinal
                """, (rs, rowNum) -> new Chunk(
                    rs.getString("source"), rs.getInt("ordinal"), rs.getString("content"),
                    parseHeadings(rs.getString("headings"))));
    }

    private static List<Window> generateWindows(List<Chunk> originals) {
        var grouped = new LinkedHashMap<String, List<Chunk>>();
        for (Chunk chunk : originals) {
            grouped.computeIfAbsent(chunk.source(), ignored -> new ArrayList<>()).add(chunk);
        }
        var result = new ArrayList<Window>();
        for (var entry : grouped.entrySet()) {
            List<Chunk> doc = entry.getValue();
            if (doc.size() < 2) continue; // A one-chunk source gains no extra context.
            for (int i = 0; i < doc.size(); i++) {
                Chunk center = doc.get(i);
                if (center.ordinal() % 2 != 0) continue; // Explicit even DB ordinals: 0,2,4,...
                int left = Math.max(0, i - 1), right = Math.min(doc.size() - 1, i + 1);
                var content = new StringBuilder();
                var covered = new ArrayList<Integer>();
                for (int j = left; j <= right; j++) {
                    if (!content.isEmpty()) content.append("\n\n");
                    content.append(doc.get(j).content());
                    covered.add(doc.get(j).ordinal());
                }
                String text = content.toString();
                String identity = EXPERIMENT + "\u0000" + center.source() + "\u0000"
                        + center.ordinal() + "\u0000" + text;
                UUID id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
                result.add(new Window(id, center.source(), center.ordinal(), text,
                        center.headings(), List.copyOf(covered)));
            }
        }
        return result;
    }

    private static List<String> parseHeadings(String value) {
        try {
            return JSON.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid headingPath JSON", exception);
        }
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot encode JSON metadata", exception);
        }
    }

    private static String toPgVector(float[] values) {
        var out = new StringBuilder(values.length * 13).append('[');
        for (int i = 0; i < values.length; i++) {
            if (i != 0) out.append(',');
            out.append(Float.toString(values[i]));
        }
        return out.append(']').toString();
    }

    private static void verifyDimensions(JdbcTemplate jdbc) {
        String type = jdbc.queryForObject("""
                SELECT format_type(atttypid, atttypmod) FROM pg_attribute
                WHERE attrelid='vector_store'::regclass AND attname='embedding'
                """, String.class);
        if (!("vector(" + DIMENSIONS + ")").equals(type)) {
            throw new IllegalStateException("Unexpected vector column: " + type);
        }
    }

    private static void rollback(JdbcTemplate jdbc) {
        int removed = jdbc.update("""
                DELETE FROM vector_store
                WHERE metadata->>'r4r_experiment' = ?
                """, EXPERIMENT);
        System.out.printf("ROLLBACK removed_experimental_vectors=%d%n", removed);
    }
}
