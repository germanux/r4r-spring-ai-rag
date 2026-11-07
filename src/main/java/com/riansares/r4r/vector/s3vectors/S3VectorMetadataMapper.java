package com.riansares.r4r.vector.s3vectors;

import com.riansares.r4r.chunking.MarkdownChunk;

import java.util.Map;

/**
 * Phase 3 skeleton for source, headingPath, ordinal and source_text metadata.
 * Only source_text is non-filterable in the existing index. Mapping to AWS SDK
 * Document values, byte limits and validation remain unimplemented.
 */
public final class S3VectorMetadataMapper {

    public Map<String, Object> toMetadata(MarkdownChunk chunk) {
        throw new UnsupportedOperationException("S3 Vectors metadata encoding is not implemented");
    }

    public MarkdownChunk fromMetadata(Map<String, Object> metadata) {
        throw new UnsupportedOperationException("S3 Vectors metadata decoding is not implemented");
    }
}
