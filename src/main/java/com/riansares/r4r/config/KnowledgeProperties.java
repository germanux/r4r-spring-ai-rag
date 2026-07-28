package com.riansares.r4r.config;

import java.nio.file.Path;

public record KnowledgeProperties(Path root, int maxFileBytes, int maxChunkChars) {

    public KnowledgeProperties {
        root = root == null ? Path.of("knowledge") : root;
        maxFileBytes = maxFileBytes <= 0 ? 1_048_576 : maxFileBytes;
        maxChunkChars = maxChunkChars <= 0 ? 2_000 : maxChunkChars;
    }
}
