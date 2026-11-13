package com.riansares.r4r.ingestion;

import com.riansares.r4r.chunking.HeadingMarkdownChunker;
import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.document.KnowledgeDocument;
import com.riansares.r4r.document.MarkdownDocumentLoader;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class KnowledgeIngestionService {

    private final JdbcTemplate jdbcTemplate;
    private final MarkdownDocumentLoader documentLoader;
    private final HeadingMarkdownChunker chunker;

    public KnowledgeIngestionService(
            JdbcTemplate jdbcTemplate,
            MarkdownDocumentLoader documentLoader,
            HeadingMarkdownChunker chunker) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.documentLoader = Objects.requireNonNull(documentLoader, "documentLoader");
        this.chunker = Objects.requireNonNull(chunker, "chunker");
    }

    @Transactional
    public KnowledgeIngestionResult ingest(Clock clock) {
        Instant start = clock.instant();

        int discoveredSources = 0;
        int changedSources = 0;
        int unchangedSources = 0;
        int deletedSources = 0;
        int persistedChunks = 0;

        try {
            for (KnowledgeDocument document : documentLoader.loadAll()) {
                discoveredSources++;

                byte[] documentChecksum = sha256(
                        document.content().getBytes(StandardCharsets.UTF_8));

                Long unchangedSourceId = findSourceIdByPathAndSha256(
                        document.source(),
                        documentChecksum);

                if (unchangedSourceId != null) {
                    unchangedSources++;
                    continue;
                }

                long sourceId = insertOrUpdateSource(
                        document.source(),
                        documentChecksum);
                changedSources++;

                List<MarkdownChunk> chunks = chunker.chunk(document);
                replaceChunks(sourceId, chunks);
                persistedChunks += chunks.size();
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to ingest knowledge documents",
                    exception);
        }

        Instant end = clock.instant();
        long durationMs = java.time.Duration.between(start, end).toMillis();

        return new KnowledgeIngestionResult(
                discoveredSources,
                changedSources,
                unchangedSources,
                deletedSources,
                persistedChunks,
                durationMs);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore> indexedStore;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModel;

    /** Complete NAS workflow. The legacy documentary-only entry point remains compatible. */
    @Transactional(rollbackFor = Exception.class)
    public java.util.Map<String, Object> ingestIndexed(boolean checkOnly, boolean replaceAll) throws Exception {
        var documents = documentLoader.loadBilingual();
        var chunks = new java.util.LinkedHashMap<String, List<MarkdownChunk>>();
        for (var document : documents) {
            // Metadata is preserved for source checksums but is not evidence to embed.
            String body = document.content().split("---", 3)[2].strip();
            var parts = chunker.chunk(new KnowledgeDocument(document.source(), body));
            if (parts.isEmpty()) throw new IllegalArgumentException("Empty knowledge document: " + document.source());
            chunks.put(document.source(), parts);
        }
        var identity = jdbcTemplate.queryForMap("""
                SELECT host(inet_server_addr()) AS server, current_database() AS database,
                       current_user AS db_user, current_setting('transaction_read_only') AS read_only
                """);
        String configuredHost = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                java.net.URI.create(connection.getMetaData().getURL().substring(5)).getHost());
        if (!"192.168.1.203".equals(configuredHost))
            throw new IllegalStateException("Complete ingestion requires the definitive NAS at 192.168.1.203");
        if (!"off".equals(identity.get("read_only"))) throw new IllegalStateException("Database is read-only");
        for (String table : List.of("knowledge_sources", "knowledge_chunks", "vector_store")) {
            Boolean allowed = jdbcTemplate.queryForObject(
                    "SELECT bool_and(has_table_privilege(current_user, ?, p)) FROM unnest(ARRAY['SELECT','INSERT','UPDATE','DELETE']) p", Boolean.class, table);
            if (!Boolean.TRUE.equals(allowed)) throw new IllegalStateException("Insufficient table privileges: " + table);
        }
        Boolean sequence = jdbcTemplate.queryForObject("""
                SELECT has_sequence_privilege(current_user, pg_get_serial_sequence('knowledge_sources','id'), 'USAGE')
                   AND has_sequence_privilege(current_user, pg_get_serial_sequence('knowledge_chunks','id'), 'USAGE')
                """, Boolean.class);
        if (!Boolean.TRUE.equals(sequence)) throw new IllegalStateException("Insufficient sequence privileges");
        String type = jdbcTemplate.queryForObject("""
                SELECT format_type(atttypid, atttypmod) FROM pg_attribute
                WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'
                """, String.class);
        if (!"vector(768)".equals(type)) throw new IllegalStateException("Expected vector(768)");
        // Uses the existing router: model name, digest, batch shape and finite 768-D vectors are checked there.
        float[] probe = embeddingModel.getObject().embed("search_document: R4R ingestion compatibility check");
        if (probe.length != 768) throw new IllegalStateException("Expected 768 embedding dimensions");
        var result = new java.util.LinkedHashMap<String, Object>(identity);
        result.put("targetHost", configuredHost);
        result.put("documents", documents.size());
        result.put("expectedChunks", chunks.values().stream().mapToInt(List::size).sum());
        result.put("checkOnly", checkOnly);
        if (checkOnly) {
            addCounts(result);
            return result;
        }
        // One writer and atomic publication across the three existing tables. No DDL or TRUNCATE.
        jdbcTemplate.execute("LOCK TABLE knowledge_sources, knowledge_chunks, vector_store IN EXCLUSIVE MODE");
        var existing = new java.util.HashSet<>(jdbcTemplate.queryForList(
                "SELECT source_path FROM knowledge_sources", String.class));
        existing.addAll(jdbcTemplate.queryForList("SELECT DISTINCT metadata->>'source' FROM vector_store", String.class));
        existing.removeAll(chunks.keySet());
        if (!existing.isEmpty() && !replaceAll)
            throw new IllegalStateException("Obsolete sources present; explicit --r4r.ingestion.replace-all=true required");
        if (replaceAll) {
            jdbcTemplate.update("DELETE FROM vector_store");
            jdbcTemplate.update("DELETE FROM knowledge_chunks");
            jdbcTemplate.update("DELETE FROM knowledge_sources");
        }
        var store = indexedStore.getObject();
        for (var document : documents) {
            long sourceId = insertOrUpdateSource(document.source(), sha256(document.content().getBytes(StandardCharsets.UTF_8)));
            var parts = chunks.get(document.source());
            replaceChunks(sourceId, parts);
            store.index(parts);
            System.out.println("R4R_INDEXED_SOURCE=" + document.source() + " chunks=" + parts.size());
        }
        addCounts(result);
        int expected = (Integer) result.get("expectedChunks");
        if (((Number) result.get("sources")).intValue() != documents.size()
                || ((Number) result.get("chunks")).intValue() != expected
                || ((Number) result.get("vectors")).intValue() != expected)
            throw new IllegalStateException("Source/chunk/vector counts disagree");
        Long mismatches = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM knowledge_chunks c JOIN knowledge_sources s ON s.id=c.source_id
                FULL JOIN vector_store v ON v.metadata->>'source'=s.source_path
                    AND (v.metadata->>'ordinal')::integer=c.ordinal
                WHERE c.id IS NULL OR v.id IS NULL OR c.content IS DISTINCT FROM v.content
                    OR to_jsonb(c.heading_path) IS DISTINCT FROM v.metadata->'headingPath'
                """, Long.class);
        if (mismatches == null || mismatches != 0) throw new IllegalStateException("Vector content/metadata mismatch");
        result.put("success", true);
        return result;
    }

    private void addCounts(java.util.Map<String, Object> result) {
        result.put("sources", jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_sources", Long.class));
        result.put("chunks", jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_chunks", Long.class));
        result.put("vectors", jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long.class));
    }

    @Transactional
    public void ingest() {
        ingest(Clock.systemUTC());
    }

    private Long findSourceIdByPathAndSha256(String sourcePath, byte[] checksum) {
        String sql = """
                SELECT id
                FROM knowledge_sources
                WHERE source_path = ?
                  AND content_sha256 = ?
                """;

        try {
            return jdbcTemplate.queryForObject(sql, Long.class, sourcePath, checksum);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private long insertOrUpdateSource(String sourcePath, byte[] checksum) {
        String sql = """
                INSERT INTO knowledge_sources (
                    source_path,
                    content_sha256
                )
                VALUES (?, ?)
                ON CONFLICT (source_path)
                DO UPDATE SET
                    content_sha256 = EXCLUDED.content_sha256,
                    updated_at = now()
                RETURNING id
                """;

        Long sourceId = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                sourcePath,
                checksum);

        if (sourceId == null) {
            throw new IllegalStateException(
                    "Database did not return a source id for " + sourcePath);
        }

        return sourceId;
    }

    private void replaceChunks(long sourceId, List<MarkdownChunk> chunks) {
        jdbcTemplate.update(
                "DELETE FROM knowledge_chunks WHERE source_id = ?",
                sourceId);

        String insertSql = """
                INSERT INTO knowledge_chunks (
                    source_id,
                    heading_path,
                    ordinal,
                    content_sha256,
                    content
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                for (int ordinal = 0; ordinal < chunks.size(); ordinal++) {
                    MarkdownChunk chunk = chunks.get(ordinal);
                    Array headingPath = connection.createArrayOf(
                            "text",
                            chunk.headingPath().toArray(String[]::new));

                    try {
                        statement.clearParameters();
                        statement.setLong(1, sourceId);
                        statement.setArray(2, headingPath);
                        statement.setInt(3, ordinal);
                        statement.setBytes(
                                4,
                                sha256(chunk.content().getBytes(StandardCharsets.UTF_8)));
                        statement.setString(5, chunk.content());
                        statement.executeUpdate();
                    } finally {
                        headingPath.free();
                    }
                }
            }
            return null;
        });
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    exception);
        }
    }
}
