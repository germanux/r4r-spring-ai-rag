package com.riansares.r4r.vector.s3vectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.chunking.MarkdownChunk;
import software.amazon.awssdk.core.document.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict decoding; source_text is the only non-filterable field in the existing index. */
public final class S3VectorMetadataMapper {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String HEADING_CONTEXT_VERSION = "pgvector-heading-v1";

    public Map<String, Object> toMetadata(MarkdownChunk chunk) {
        if (chunk == null) throw new IllegalArgumentException("chunk must not be null");
        Map<String, Object> metadata = new LinkedHashMap<>(Map.of("source", chunk.source(),
                "ordinal", chunk.index(), "source_text", chunk.content()));
        if (!chunk.headingPath().isEmpty()) metadata.put("headingPath", chunk.headingPath());
        fromMetadata(metadata); // Same validation on both directions; never truncate evidence.
        return Map.copyOf(metadata);
    }

    public MarkdownChunk fromMetadata(Map<String, Object> metadata) {
        if (metadata == null) throw invalid("missing metadata");
        if (metadata.size() > 50) throw invalid("too many metadata keys");
        checkBudget(metadata, 40 * 1024, "total metadata");
        Map<String, Object> filterable = new LinkedHashMap<>(metadata);
        filterable.remove("source_text");
        checkBudget(filterable, 2 * 1024, "filterable metadata");
        String source = requiredString(metadata, "source");
        String text = requiredString(metadata, "source_text");
        Object headingPath = metadata.containsKey("headingPath") ? metadata.get("headingPath") : List.of();
        if (!(headingPath instanceof List<?> values)) throw invalid("headingPath must be a list");
        List<String> headings = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String heading)) throw invalid("headingPath must contain strings");
            headings.add(heading);
        }
        if (!(metadata.get("ordinal") instanceof Number number)) throw invalid("ordinal must be numeric");
        int ordinal;
        try {
            ordinal = new BigDecimal(number.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw invalid("ordinal must be an integer");
        }
        if (ordinal < 0) throw invalid("ordinal must be non-negative");
        if (text.strip().matches("#{1,6}\\s+[^\\r\\n]+")
                && !HEADING_CONTEXT_VERSION.equals(metadata.get("r4r_context"))) {
            throw invalid("heading-only source_text requires manual PG context expansion or verified r4r_context marker");
        }
        return new MarkdownChunk(source, headings, ordinal, text);
    }

    public MarkdownChunk fromDocument(Document metadata) {
        if (metadata == null || !metadata.isMap()) throw invalid("metadata must be an object");
        Map<String, Object> values = new LinkedHashMap<>();
        metadata.asMap().forEach((key, value) -> values.put(key, javaValue(value)));
        return fromMetadata(values);
    }

    private static Object javaValue(Document value) {
        if (value == null || value.isNull()) return null;
        // SDK Document.unwrap() turns numbers into strings; retain the actual JSON type.
        if (value.isNumber()) return value.asNumber().bigDecimalValue();
        if (value.isString()) return value.asString();
        if (value.isBoolean()) return value.asBoolean();
        if (value.isList()) return value.asList().stream().map(S3VectorMetadataMapper::javaValue).toList();
        if (value.isMap()) {
            Map<String, Object> result = new LinkedHashMap<>();
            value.asMap().forEach((key, child) -> result.put(key, javaValue(child)));
            return result;
        }
        throw invalid("unsupported metadata value");
    }

    private static String requiredString(Map<String, Object> metadata, String key) {
        if (!(metadata.get(key) instanceof String text) || text.isBlank()) throw invalid(key + " must be non-blank");
        return text;
    }

    // Conservative UTF-8 JSON budget includes keys, delimiters and escaping, not just character counts.
    private static void checkBudget(Map<String, Object> metadata, int limit, String field) {
        try {
            if (JSON.writeValueAsBytes(metadata).length > limit) throw invalid(field + " exceeds byte budget");
        } catch (JsonProcessingException exception) {
            throw invalid("metadata is not JSON serializable");
        }
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Invalid S3 vector metadata: " + reason);
    }
}
