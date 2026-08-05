package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class PgVectorKnowledgeStore {

    static final String SOURCE_METADATA = "source";
    static final String HEADING_PATH_METADATA = "headingPath";
    static final String ORDINAL_METADATA = "ordinal";

    private static final String DELETE_SOURCE_SQL =
            "DELETE FROM vector_store WHERE metadata->>'source' = ?";

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public PgVectorKnowledgeStore(
            VectorStore vectorStore,
            JdbcTemplate jdbcTemplate) {

        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    /**
     * Replaces every source represented by the supplied chunks.
     * The complete request is validated before the first database mutation.
     */
    @Transactional
    public void index(List<MarkdownChunk> chunks) {
        if (chunks == null) {
            throw new IllegalArgumentException("chunks must not be null");
        }

        Map<String, List<MarkdownChunk>> chunksBySource = new LinkedHashMap<>();
        for (MarkdownChunk chunk : chunks) {
            validateChunk(chunk);
            chunksBySource
                    .computeIfAbsent(chunk.source(), ignored -> new ArrayList<>())
                    .add(chunk);
        }

        Map<String, List<Document>> preparedBatches = new LinkedHashMap<>();
        for (Map.Entry<String, List<MarkdownChunk>> entry
                : chunksBySource.entrySet()) {

            preparedBatches.put(
                    entry.getKey(),
                    prepareDocuments(entry.getKey(), entry.getValue()));
        }

        for (Map.Entry<String, List<Document>> entry
                : preparedBatches.entrySet()) {

            replacePreparedSource(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Replaces one source explicitly. An empty list removes that source.
     */
    @Transactional
    public void replaceSource(
            String source,
            List<MarkdownChunk> chunks) {

        requireNonBlank(source, "source");
        if (chunks == null) {
            throw new IllegalArgumentException("chunks must not be null");
        }

        for (MarkdownChunk chunk : chunks) {
            validateChunk(chunk);
            if (!source.equals(chunk.source())) {
                throw new IllegalArgumentException(
                        "Every chunk must belong to source " + source);
            }
        }

        List<Document> documents = prepareDocuments(source, chunks);
        replacePreparedSource(source, documents);
    }

    public List<MarkdownChunk> search(
            String query,
            int topK,
            double minScore) {

        requireNonBlank(query, "query");
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        if (!Double.isFinite(minScore)
                || minScore < 0.0
                || minScore > 1.0) {

            throw new IllegalArgumentException(
                    "minScore must be finite and between 0.0 and 1.0");
        }

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(minScore)
                .build();

        return vectorStore.similaritySearch(request).stream()
                .map(this::fromDocument)
                .toList();
    }

    private List<Document> prepareDocuments(
            String source,
            List<MarkdownChunk> chunks) {

        Set<String> ids = new HashSet<>();
        List<Document> documents = new ArrayList<>(chunks.size());

        for (MarkdownChunk chunk : chunks) {
            Document document = toDocument(chunk);
            if (!ids.add(document.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate logical chunk identity for source "
                                + source
                                + ": ordinal "
                                + chunk.index());
            }
            documents.add(document);
        }

        return List.copyOf(documents);
    }

    private void replacePreparedSource(
            String source,
            List<Document> documents) {

        jdbcTemplate.update(DELETE_SOURCE_SQL, source);
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }

    private Document toDocument(MarkdownChunk chunk) {
        Map<String, Object> metadata = Map.of(
                SOURCE_METADATA, chunk.source(),
                HEADING_PATH_METADATA, chunk.headingPath(),
                ORDINAL_METADATA, chunk.index());

        return Document.builder()
                .id(stableId(chunk).toString())
                .text(chunk.content())
                .metadata(metadata)
                .build();
    }

    MarkdownChunk fromDocument(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }

        String content = document.getText();
        if (content == null) {
            throw new IllegalStateException(
                    "Retrieved vector document has null text");
        }

        Map<String, Object> metadata = document.getMetadata();
        if (metadata == null) {
            throw new IllegalStateException(
                    "Retrieved vector document has no metadata");
        }

        String source = metadataString(metadata, SOURCE_METADATA);
        List<String> headingPath =
                metadataStringList(metadata, HEADING_PATH_METADATA);
        int ordinal = metadataOrdinal(metadata, ORDINAL_METADATA);

        return new MarkdownChunk(source, headingPath, ordinal, content);
    }

    private static void validateChunk(MarkdownChunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk must not be null");
        }
        requireNonBlank(chunk.source(), "chunk.source");
        requireNonBlank(chunk.content(), "chunk.content");
    }

    private static String metadataString(
            Map<String, Object> metadata,
            String key) {

        Object value = metadata.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException(
                    "Vector metadata '" + key + "' must be a non-blank string");
        }
        return text;
    }

    private static List<String> metadataStringList(
            Map<String, Object> metadata,
            String key) {

        Object value = metadata.get(key);
        if (!(value instanceof Collection<?> values)) {
            throw new IllegalStateException(
                    "Vector metadata '" + key + "' must be an array");
        }

        List<String> result = new ArrayList<>(values.size());
        for (Object element : values) {
            if (!(element instanceof String text)) {
                throw new IllegalStateException(
                        "Vector metadata '"
                                + key
                                + "' contains a non-string value");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static int metadataOrdinal(
            Map<String, Object> metadata,
            String key) {

        Object value = metadata.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException(
                    "Vector metadata '" + key + "' must be numeric");
        }

        int ordinal = number.intValue();
        if (ordinal < 0 || number.doubleValue() != (double) ordinal) {
            throw new IllegalStateException(
                    "Vector metadata '"
                            + key
                            + "' must be a non-negative integer");
        }
        return ordinal;
    }

    private static UUID stableId(MarkdownChunk chunk) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateLengthPrefixed(digest, chunk.source());

            digest.update(intBytes(chunk.headingPath().size()));
            for (String heading : chunk.headingPath()) {
                updateLengthPrefixed(digest, heading);
            }

            digest.update(intBytes(chunk.index()));

            byte[] bytes = digest.digest();
            bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x50);
            bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);

            ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, 16);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception);
        }
    }

    private static void updateLengthPrefixed(
            MessageDigest digest,
            String value) {

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(intBytes(bytes.length));
        digest.update(bytes);
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES)
                .putInt(value)
                .array();
    }

    private static void requireNonBlank(
            String value,
            String field) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }
}
