package com.riansares.r4r.chunking;

import java.util.List;
import java.util.Objects;

public record MarkdownChunk(String source, List<String> headingPath, int index, String content) {

    public MarkdownChunk {
        source = Objects.requireNonNull(source, "source");
        headingPath = List.copyOf(Objects.requireNonNull(headingPath, "headingPath"));
        content = Objects.requireNonNull(content, "content");
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
    }
}
