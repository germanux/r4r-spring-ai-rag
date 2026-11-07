package com.riansares.r4r.vector.s3vectors;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;

import java.util.List;

/**
 * Phase 3 skeleton, deliberately not registered as a Spring bean.
 * Consumers use {@link KnowledgeVectorStore}, never this concrete class.
 * Future construction requires AWS SDK v2 S3VectorsClient, the existing Spring AI
 * EmbeddingModel, vector properties and metadata/score mappers. SDK dependency,
 * constructor injection and all behavior are deferred to phase 3 implementation.
 * Initial corpus transfer remains manual and reuses existing PostgreSQL vectors.
 */
public final class S3VectorsKnowledgeStore implements KnowledgeVectorStore {

    @Override
    public void index(List<MarkdownChunk> chunks) {
        throw new UnsupportedOperationException("S3 Vectors index is not implemented");
    }

    @Override
    public void replaceSource(String source, List<MarkdownChunk> chunks) {
        throw new UnsupportedOperationException("S3 Vectors replaceSource is not implemented");
    }

    @Override
    public List<MarkdownChunk> search(String query, int topK, double minScore) {
        throw new UnsupportedOperationException("S3 Vectors search is not implemented");
    }
}
