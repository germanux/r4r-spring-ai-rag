package com.riansares.r4r.chunking;

import com.riansares.r4r.document.KnowledgeDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeadingMarkdownChunkerTest {

    @Test
    void preservesNestedHeadingPath() {
        var document = new KnowledgeDocument("sample.md", """
                # Root
                Intro.
                ## Child
                Details.
                """);

        var chunks = new HeadingMarkdownChunker(200).chunk(document);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).headingPath()).containsExactly("Root");
        assertThat(chunks.get(1).headingPath()).containsExactly("Root", "Child");
    }

    @Test
    void splitsLargeSectionsWithoutExceedingTheLimit() {
        String body = "word ".repeat(80);
        var document = new KnowledgeDocument("large.md", "# Large\n" + body);

        var chunks = new HeadingMarkdownChunker(96).chunk(document);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> chunk.content().length() <= 96);
    }
}
