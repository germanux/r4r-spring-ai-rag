package com.riansares.r4r.service;

import com.riansares.r4r.chunking.HeadingMarkdownChunker;
import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.document.KnowledgeDocument;
import com.riansares.r4r.document.MarkdownDocumentLoader;

import java.io.IOException;
import java.util.List;

public final class KnowledgeCatalogService {

    private final MarkdownDocumentLoader loader;
    private final HeadingMarkdownChunker chunker;

    public KnowledgeCatalogService(MarkdownDocumentLoader loader, HeadingMarkdownChunker chunker) {
        this.loader = loader;
        this.chunker = chunker;
    }

    public CatalogSummary catalog() throws IOException {
        List<KnowledgeDocument> documents = loader.loadAll();
        List<MarkdownChunk> chunks = documents.stream()
                .flatMap(document -> chunker.chunk(document).stream())
                .toList();
        long totalCharacters = documents.stream().mapToLong(document -> document.content().length()).sum();
        List<String> sources = documents.stream().map(KnowledgeDocument::source).toList();
        return new CatalogSummary(documents.size(), chunks.size(), totalCharacters, sources);
    }
}
