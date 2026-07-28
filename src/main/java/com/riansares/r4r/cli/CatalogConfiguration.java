package com.riansares.r4r.cli;

import com.riansares.r4r.chunking.HeadingMarkdownChunker;
import com.riansares.r4r.config.KnowledgeProperties;
import com.riansares.r4r.document.MarkdownDocumentLoader;
import com.riansares.r4r.service.KnowledgeCatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class CatalogConfiguration {

    @Bean
    KnowledgeProperties knowledgeProperties(
            @Value("${r4r.knowledge.root:knowledge}") Path root,
            @Value("${r4r.knowledge.max-file-bytes:1048576}") int maxFileBytes,
            @Value("${r4r.knowledge.max-chunk-chars:2000}") int maxChunkChars) {
        return new KnowledgeProperties(root, maxFileBytes, maxChunkChars);
    }

    @Bean
    MarkdownDocumentLoader markdownDocumentLoader(KnowledgeProperties properties) {
        return new MarkdownDocumentLoader(properties);
    }

    @Bean
    HeadingMarkdownChunker headingMarkdownChunker(KnowledgeProperties properties) {
        return new HeadingMarkdownChunker(properties.maxChunkChars());
    }

    @Bean
    KnowledgeCatalogService knowledgeCatalogService(
            MarkdownDocumentLoader loader,
            HeadingMarkdownChunker chunker) {
        return new KnowledgeCatalogService(loader, chunker);
    }

    @Bean
    @ConditionalOnProperty(name = "r4r.catalog-on-startup", havingValue = "true")
    ApplicationRunner catalogRunner(KnowledgeCatalogService service) {
        return arguments -> {
            var summary = service.catalog();
            System.out.printf(
                    "R4R catalog: documents=%d chunks=%d characters=%d%n",
                    summary.documentCount(),
                    summary.chunkCount(),
                    summary.totalCharacters());
        };
    }
}
