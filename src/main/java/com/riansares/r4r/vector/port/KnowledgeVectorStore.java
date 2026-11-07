package com.riansares.r4r.vector.port;

import com.riansares.r4r.chunking.MarkdownChunk;

import java.util.List;

/** Storage and retrieval of documentary evidence, independent of the vector provider. */
public interface KnowledgeVectorStore {

    /**
     * Replaces each source represented in the list, leaving other sources unchanged.
     * The complete request is validated before writing; an empty list is a no-op.
     * Transactional guarantees belong to the implementing provider.
     */
    void index(List<MarkdownChunk> chunks);

    /** Replaces one source; an empty list removes all its chunks. */
    void replaceSource(String source, List<MarkdownChunk> chunks);

    /**
     * Returns up to {@code topK} chunks in retrieval order above the strict
     * similarity threshold, including any provider-side evidence context.
     * A valid empty result indicates no evidence; infrastructure errors propagate.
     */
    List<MarkdownChunk> search(String query, int topK, double minScore);
}
