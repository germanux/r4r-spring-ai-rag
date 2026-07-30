package com.riansares.r4r.document;

import com.riansares.r4r.config.KnowledgeProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class MarkdownDocumentLoader {

    private final KnowledgeProperties properties;

    public MarkdownDocumentLoader(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public List<KnowledgeDocument> loadAll() throws IOException {
        Path root = properties.root().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isMarkdown)
                    .sorted(Comparator.comparing(path -> normalizedRelativePath(root, path)))
                    .map(path -> read(root, path))
                    .toList();
        }
    }

    private KnowledgeDocument read(Path root, Path path) {
        try {
            long size = Files.size(path);
            if (size > properties.maxFileBytes()) {
                throw new IllegalArgumentException(
                        "Markdown file exceeds maxFileBytes: " + normalizedRelativePath(root, path));
            }
            return new KnowledgeDocument(
                    normalizedRelativePath(root, path),
                    Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Markdown file: " + path, exception);
        }
    }

    private boolean isMarkdown(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private String normalizedRelativePath(Path root, Path path) {
        return root.relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace(path.getFileSystem().getSeparator(), "/");
    }
}
