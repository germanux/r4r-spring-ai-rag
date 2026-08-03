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
                persistedChunks,
                durationMs);
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
