package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.ai.model.embedding=none")
@ActiveProfiles("test")
@Import(PgVectorTestConfiguration.class)
class PgVectorKnowledgeStoreTransactionalRollbackIT {

    private static final String TRIGGER_NAME =
            "fail_vector_store_insert_trigger";
    private static final String FUNCTION_NAME =
            "fail_vector_store_insert";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PgVectorKnowledgeStore store;

    @BeforeEach
    void prepareDatabase() {
        dropFailureObjects();
        jdbcTemplate.execute("TRUNCATE TABLE vector_store");
    }

    @AfterEach
    void cleanupDatabase() {
        dropFailureObjects();
        jdbcTemplate.execute("TRUNCATE TABLE vector_store");
    }

    @Test
    void replaceSourceRollsBackDeleteWhenRealInsertFails() {
        String source = "rollback-source.md";

        store.replaceSource(source, List.of(
                new MarkdownChunk(
                        source,
                        List.of("Section"),
                        0,
                        "Original content zero"),
                new MarkdownChunk(
                        source,
                        List.of("Section"),
                        1,
                        "Original content one")));

        List<VectorRowSnapshot> before = snapshot(source);
        installFailureTrigger();

        try {
            assertThatThrownBy(() -> store.replaceSource(source, List.of(
                    new MarkdownChunk(
                            source,
                            List.of("Section"),
                            0,
                            "Replacement content"))))
                    .isInstanceOf(RuntimeException.class)
                    .hasStackTraceContaining(
                            "forced vector store insert failure");
        } finally {
            dropFailureObjects();
        }

        List<VectorRowSnapshot> after = snapshot(source);
        assertThat(after).isEqualTo(before);
    }

    private void installFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fail_vector_store_insert()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'forced vector store insert failure';
                END;
                $$
                """);

        jdbcTemplate.execute("""
                CREATE TRIGGER fail_vector_store_insert_trigger
                BEFORE INSERT ON vector_store
                FOR EACH ROW
                EXECUTE FUNCTION fail_vector_store_insert()
                """);
    }

    private void dropFailureObjects() {
        jdbcTemplate.execute("""
                DROP TRIGGER IF EXISTS fail_vector_store_insert_trigger
                ON vector_store
                """);
        jdbcTemplate.execute("""
                DROP FUNCTION IF EXISTS fail_vector_store_insert()
                """);
    }

    private List<VectorRowSnapshot> snapshot(String source) {
        return jdbcTemplate.query("""
                SELECT id::text,
                       content,
                       metadata::text,
                       embedding::text
                FROM vector_store
                WHERE metadata->>'source' = ?
                ORDER BY (metadata->>'ordinal')::int, id
                """, (resultSet, rowNumber) -> new VectorRowSnapshot(
                resultSet.getString(1),
                resultSet.getString(2),
                resultSet.getString(3),
                resultSet.getString(4)), source);
    }

    private record VectorRowSnapshot(
            String id,
            String content,
            String metadata,
            String embedding) {
    }
}
