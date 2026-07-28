package com.riansares.r4r.service;

import com.riansares.r4r.chunking.HeadingMarkdownChunker;
import com.riansares.r4r.config.KnowledgeProperties;
import com.riansares.r4r.document.MarkdownDocumentLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeCatalogServiceTest {

    @TempDir
    Path root;

    @Test
    void createsACompactCatalogSummary() throws Exception {
        Files.writeString(root.resolve("one.md"), "# One\nText\n## Two\nMore text");
        var properties = new KnowledgeProperties(root, 1024, 200);
        var service = new KnowledgeCatalogService(
                new MarkdownDocumentLoader(properties),
                new HeadingMarkdownChunker(properties.maxChunkChars()));

        var summary = service.catalog();

        assertThat(summary.documentCount()).isEqualTo(1);
        assertThat(summary.chunkCount()).isEqualTo(2);
        assertThat(summary.sources()).containsExactly("one.md");
        assertThat(summary.totalCharacters()).isPositive();
    }
}
