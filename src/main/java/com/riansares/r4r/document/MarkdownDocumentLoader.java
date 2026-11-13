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

    /** Strict bilingual corpus selection for the complete ingestion CLI. */
    public List<KnowledgeDocument> loadBilingual() throws IOException {
        return loadBilingualCorpus().documents();
    }

    /** One validated snapshot, including explicit exclusions for selective database cleanup. */
    public record BilingualCorpus(List<KnowledgeDocument> documents, List<String> excludedSources) { }

    public BilingualCorpus loadBilingualCorpus() throws IOException {
        var documents = loadAll().stream()
                .filter(d -> d.source().matches("(?:es|en)/(?:[^/]+/)*R4R_\\d+_[^/]+\\.md"))
                .toList();
        var ids = new java.util.HashMap<String, java.util.Set<String>>();
        ids.put("es", new java.util.HashSet<>());
        ids.put("en", new java.util.HashSet<>());
        var selected = new java.util.ArrayList<KnowledgeDocument>();
        var excluded = new java.util.ArrayList<String>();
        for (var document : documents) {
            String language = document.source().substring(0, 2);
            var matcher = java.util.regex.Pattern.compile("R4R_(\\d+)_").matcher(document.source());
            if (!matcher.find() || !ids.get(language).add(matcher.group(1)))
                throw new IllegalArgumentException("Duplicate document identifier: " + document.source());
            String[] parts = document.content().split("---", 3);
            if (parts.length != 3 || !parts[0].isBlank()
                    || !java.util.regex.Pattern.compile("(?m)^language: *[\"']?" + language + "[\"']? *$")
                    .matcher(parts[1]).find())
                throw new IllegalArgumentException("Invalid language/frontmatter: " + document.source());
            if (isExcludedFromEditorialRag(document)) excluded.add(document.source());
            else selected.add(document);
        }
        if (ids.get("es").isEmpty() || !ids.get("es").equals(ids.get("en"))) {
            var missingEn = new java.util.TreeSet<>(ids.get("es")); missingEn.removeAll(ids.get("en"));
            var missingEs = new java.util.TreeSet<>(ids.get("en")); missingEs.removeAll(ids.get("es"));
            throw new IllegalArgumentException("Missing translation pairs: en=" + missingEn + ", es=" + missingEs);
        }
        var selectedEs = new java.util.HashSet<String>();
        var selectedEn = new java.util.HashSet<String>();
        for (var document : selected) {
            String name = Path.of(document.source()).getFileName().toString();
            String id = name.substring(0, name.indexOf('_', 4));
            (document.source().startsWith("es/") ? selectedEs : selectedEn).add(id);
        }
        if (!selectedEs.equals(selectedEn))
            throw new IllegalArgumentException("Translation pair ingestion flags disagree");
        return new BilingualCorpus(List.copyOf(selected), List.copyOf(excluded));
    }

    /** Only frontmatter can opt a source out; matching text in its body has no effect. */
    public static boolean isExcludedFromEditorialRag(KnowledgeDocument document) {
        String[] parts = document.content().split("---", 3);
        return parts.length == 3 && parts[0].isBlank()
                && java.util.regex.Pattern.compile("(?m)^ingest_into_editorial_rag: *false *\r?$")
                        .matcher(parts[1]).find();
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
