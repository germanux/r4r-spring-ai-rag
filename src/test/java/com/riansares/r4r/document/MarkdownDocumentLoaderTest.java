package com.riansares.r4r.document;

import com.riansares.r4r.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownDocumentLoaderTest {

    @TempDir
    Path root;

    @Test
    void loadsMarkdownRecursivelyInStableOrder() throws Exception {
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("z.md"), "# Z");
        Files.writeString(root.resolve("nested/a.md"), "# A");
        Files.writeString(root.resolve("ignored.txt"), "ignored");

        var properties = new KnowledgeProperties(root, 1024, 200);
        var documents = new MarkdownDocumentLoader(properties).loadAll();

        assertThat(documents)
                .extracting(KnowledgeDocument::source)
                .containsExactly("nested/a.md", "z.md");
    }
}
