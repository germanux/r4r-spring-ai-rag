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
    @Test
    void bilingualSelectionRequiresPairsAndExcludesManifestAndReadme() throws Exception {
        for (String language : java.util.List.of("es", "en")) {
            Files.createDirectories(root.resolve(language));
            Files.writeString(root.resolve(language + "/R4R_00_IDENTITY.md"),
                    "---\nlanguage: " + language + "\n---\n# Identity\nEvidence");
            Files.writeString(root.resolve(language + "/R4R_70_MANIFEST.md"),
                    "---\nlanguage: " + language + "\ningest_into_editorial_rag: false\n---\n# Manifest");
            Files.writeString(root.resolve(language + "/README.md"), "# Developer notes");
        }
        var loader = new MarkdownDocumentLoader(new KnowledgeProperties(root, 4096, 200));
        assertThat(loader.loadBilingual()).extracting(KnowledgeDocument::source)
                .containsExactly("en/R4R_00_IDENTITY.md", "es/R4R_00_IDENTITY.md");
        Files.delete(root.resolve("en/R4R_00_IDENTITY.md"));
        org.assertj.core.api.Assertions.assertThatThrownBy(loader::loadBilingual)
                .hasMessageContaining("Missing translation pairs");
    }

    @Test
    void duplicateIdentifiersAndWrongLanguageFailBeforeIndexing() throws Exception {
        Files.createDirectories(root.resolve("es"));
        Files.createDirectories(root.resolve("en"));
        Files.writeString(root.resolve("es/R4R_00_A.md"), "---\nlanguage: es\n---\n# A");
        Files.writeString(root.resolve("es/R4R_00_B.md"), "---\nlanguage: es\n---\n# B");
        Files.writeString(root.resolve("en/R4R_00_A.md"), "---\nlanguage: en\n---\n# A");
        var loader = new MarkdownDocumentLoader(new KnowledgeProperties(root, 4096, 200));
        org.assertj.core.api.Assertions.assertThatThrownBy(loader::loadBilingual).hasMessageContaining("Duplicate");
        Files.delete(root.resolve("es/R4R_00_B.md"));
        Files.writeString(root.resolve("en/R4R_00_A.md"), "---\nlanguage: es\n---\n# A");
        org.assertj.core.api.Assertions.assertThatThrownBy(loader::loadBilingual).hasMessageContaining("Invalid language");
    }
    @Test
    void translationPairsMustAgreeOnIngestionExclusion() throws Exception {
        for (String language : java.util.List.of("es", "en")) {
            Files.createDirectories(root.resolve(language));
            Files.writeString(root.resolve(language + "/R4R_00_A.md"),
                    "---\nlanguage: " + language + "\ningest_into_editorial_rag: "
                            + language.equals("es") + "\n---\n# A");
        }
        var loader = new MarkdownDocumentLoader(new KnowledgeProperties(root, 4096, 200));
        org.assertj.core.api.Assertions.assertThatThrownBy(loader::loadBilingual).hasMessageContaining("flags disagree");
    }
    @Test
    void excludedDocumentsAreRetainedInTheValidatedSnapshotForCleanup() throws Exception {
        for (String language : java.util.List.of("es", "en")) {
            Files.createDirectories(root.resolve(language + "/sources"));
            Files.writeString(root.resolve(language + "/sources/R4R_71_REFERENCES.md"),
                    "---\nlanguage: " + language + "\ningest_into_editorial_rag: false\n---\n# References");
        }
        var corpus = new MarkdownDocumentLoader(new KnowledgeProperties(root, 4096, 200)).loadBilingualCorpus();
        assertThat(corpus.documents()).isEmpty();
        assertThat(corpus.excludedSources()).containsExactly(
                "en/sources/R4R_71_REFERENCES.md", "es/sources/R4R_71_REFERENCES.md");
    }

    @Test
    void bodyTextCannotExcludeADocument() {
        assertThat(MarkdownDocumentLoader.isExcludedFromEditorialRag(new KnowledgeDocument("guide.md",
                "# Guide\ningest_into_editorial_rag: false"))).isFalse();
        assertThat(MarkdownDocumentLoader.isExcludedFromEditorialRag(new KnowledgeDocument("guide.md",
                "---\nlanguage: es\n---\n# Guide\ningest_into_editorial_rag: false"))).isFalse();
    }

    @Test
    void archivedExternalReferenceDocumentsAreOutsideTheBilingualCorpus() throws Exception {
        var corpus = new MarkdownDocumentLoader(new KnowledgeProperties(Path.of("knowledge"), 1_048_576, 2000))
                .loadBilingualCorpus();
        String es = "es/sources/R4R_71_REFERENCIAS_TECNICAS_INTERNACIONALES_2026.md";
        String en = "en/sources/R4R_71_INTERNATIONAL_TECHNICAL_REFERENCES_2026.md";
        assertThat(Path.of("knowledge").resolve(es)).doesNotExist();
        assertThat(Path.of("knowledge").resolve(en)).doesNotExist();
        assertThat(Path.of("docs/knowledge/old").resolve(es)).exists();
        assertThat(Path.of("docs/knowledge/old").resolve(en)).exists();
        assertThat(corpus.excludedSources()).doesNotContain(es, en);
        assertThat(corpus.documents()).extracting(KnowledgeDocument::source).doesNotContain(es, en);
    }

}
