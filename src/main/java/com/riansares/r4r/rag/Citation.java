package com.riansares.r4r.rag;

import java.util.List;
import java.util.Objects;

/**
 * Immutable citation record preserving metadata from retrieved chunks.
 */
public record Citation(String label, String source, List<String> headingPath, int ordinal) {

    public Citation {
        label = Objects.requireNonNull(label, "label");
        source = Objects.requireNonNull(source, "source");
        headingPath = List.copyOf(Objects.requireNonNull(headingPath, "headingPath"));
    }

    /**
     * Creates a citation with the given label from a MarkdownChunk.
     */
    public static Citation fromMarkdownChunk(String label, com.riansares.r4r.chunking.MarkdownChunk chunk) {
        return new Citation(label, chunk.source(), chunk.headingPath(), chunk.index());
    }
}
