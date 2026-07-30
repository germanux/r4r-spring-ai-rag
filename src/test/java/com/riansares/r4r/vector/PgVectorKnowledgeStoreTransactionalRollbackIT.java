package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.ai.model.embedding=none")
@ActiveProfiles("test")
@Import(PgVectorTestConfiguration.class)
class PgVectorKnowledgeStoreTransactionalRollbackIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private VectorStore vectorStore;
    
    private final AtomicBoolean throwOnAdd = new AtomicBoolean(false);

    @Autowired
    private PgVectorKnowledgeStore store;

    @BeforeEach
    void clearVectorStore() {
        jdbcTemplate.execute("TRUNCATE TABLE vector_store");
    }
    
    @Test
    void indexOperationRollsBackOnAddFailure() {
        String source = "rollback-source.md";
        
        // Seed with valid 768-dimensional embedding and proper metadata.source
        float[] embedding = new float[DeterministicEmbeddingModel.DIMENSIONS];
        embedding[0] = 1.0f; // Valid unit vector for seed data
        
        StringBuilder embeddingArrayBuilder = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                embeddingArrayBuilder.append(",");
            }
            embeddingArrayBuilder.append(embedding[i]);
        }
        
        jdbcTemplate.execute("INSERT INTO vector_store (id, content, metadata, embedding) VALUES "
                + "('123e4567-e89b-12d3-a456-426614174000', 'original source content', "
                + "'{\"source\":\"rollback-source.md\",\"headingPath\":[\"Section\"],\"ordinal\":0}', "
                + "array[" + embeddingArrayBuilder.toString() + "]::vector(768))");

        // Snapshot database state before operation
        long countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'source' = ?", Long.class, source);
        
        String contentBefore = jdbcTemplate.queryForObject(
                "SELECT content FROM vector_store WHERE metadata->>'source' = ?",
                String.class, source);
        
        String metadataBefore = jdbcTemplate.queryForObject(
                "SELECT metadata::text FROM vector_store WHERE metadata->>'source' = ?",
                String.class, source);

        // Snapshot UUID before operation
        String uuidBefore = jdbcTemplate.queryForObject(
                "SELECT id::text FROM vector_store WHERE metadata->>'source' = ?",
                String.class, source);

        // Configure the spy to throw on add after delete
        doAnswer(invocation -> {
            if (throwOnAdd.get()) {
                throw new RuntimeException("Simulated add failure");
            }
            return invocation.callRealMethod();
        }).when(vectorStore).add(any(List.class));
        
        // Enable failure mode for add - this will be called after delete in the transaction
        throwOnAdd.set(true);

        assertThatThrownBy(() -> {
            List<MarkdownChunk> chunks = List.of(
                    new MarkdownChunk(source, List.of("Section"), 0, "New content to index"));
            store.index(chunks);
        }).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Simulated add failure");

        // Verify rollback - database state should be unchanged
        long countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'source' = ?", Long.class, source);
        
        String contentAfter = jdbcTemplate.queryForObject(
                "SELECT content FROM vector_store WHERE metadata->>'source' = ?",
                String.class, source);
        
        String metadataAfter = jdbcTemplate.queryForObject(
                "SELECT metadata::text FROM vector_store WHERE metadata->>'source' = ?",
                String.class, source);

        // Snapshot UUID after operation
        String uuidAfter = jdbcTemplate.queryForObject(
                "SELECT id::text FROM vector_store WHERE metadata->>'source' = ?",
                String.class, source);

        assertThat(countAfter).isEqualTo(countBefore);
        assertThat(contentAfter).isEqualTo(contentBefore);
        assertThat(metadataAfter).isEqualTo(metadataBefore);
        assertThat(uuidAfter).isEqualTo(uuidBefore);
    }
    
    @AfterEach
    void resetThrowOnAdd() {
        throwOnAdd.set(false);
    }
}
