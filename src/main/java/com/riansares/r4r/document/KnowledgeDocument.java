package com.riansares.r4r.document;

import java.util.Objects;

public record KnowledgeDocument(String source, String content) {

    public KnowledgeDocument {
        source = Objects.requireNonNull(source, "source");
        content = Objects.requireNonNull(content, "content");
    }
}
