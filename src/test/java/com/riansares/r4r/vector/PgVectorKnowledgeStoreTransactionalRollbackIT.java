package com.riansares.r4r.vector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.ai.model.embedding=none")
@ActiveProfiles("test")
@Import(PgVectorTestConfiguration.class)
class PgVectorKnowledgeStoreTransactionalRollbackIT {

    private static final String TRIGGER_NAME = "vector_store_insert_fail_trigger";
    private static final String FUNCTION_NAME = "vector_store_insert_fail_function";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PgVectorKnowledgeStore store;

    @BeforeAll
    static void cleanupStaleTriggerAndFunction() {
        // Clean up any stale trigger/function from previous runs
        try (var conn = javax.sql.DataSource.class.cast(
                org.springframework.beans.factory.BeanFactoryUtils
                        .lookup(org.springframework.context.ApplicationContext.class)
                        .getBean(javax.sql.DataSource.class)).getConnection()) {
            var stmt = conn.createStatement();
            stmt.execute("DROP TRIGGER IF EXISTS " + TRIGGER_NAME + " ON vector_store");
            stmt.execute("DROP FUNCTION IF EXISTS " + FUNCTION_NAME + "() CASCADE");
        } catch (Exception ignored) {
            // Ignore cleanup errors
        }
    }

    @BeforeEach
    void clearVectorStore() {
        jdbcTemplate.execute("TRUNCATE TABLE vector_store");
    }

    @AfterEach
    void cleanupTriggerAndFunction() {
        try {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + TRIGGER_NAME + " ON vector_store");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + FUNCTION_NAME + "() CASCADE");
        } catch (Exception ignored) {
            // Ignore cleanup errors
        }
    }

    @Test
    void indexOperationRollsBackOnInsertFailure() {
        String source = "rollback-source.md";

        // Seed with valid chunks through the real store
        store.index(List.of(
                new MarkdownChunk(source, List.of("Section"), 0, "Original content")));

        // Snapshot every relevant row before operation
        List<Map<String, Object>> beforeSnapshot = jdbcTemplate.queryForList("""
                SELECT id::text AS id,
                       content,
                       metadata::text AS metadata,
                       embedding::text AS embedding
                FROM vector_store
                WHERE metadata->>'source' = ?
                ORDER BY ordinal(metadata), id
                """, source);

        // Install BEFORE INSERT trigger that raises failure
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION """ + FUNCTION_NAME + """()
                RETURNS TRIGGER
                LANGUAGE plpgsql
                AS $func$
                BEGIN
                    RAISE EXCEPTION 'forced vector store insert failure';
                END;
                $func$""");

        jdbcTemplate.execute("""
                CREATE CONSTRAINT TRIGGER """ + TRIGGER_NAME + """
                AFTER INSERT ON vector_store
                DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW
                EXECUTE FUNCTION """ + FUNCTION_NAME + """()""");

        // Verify trigger is installed
        String triggerExists = jdbcTemplate.queryForObject("""
                SELECT tgname FROM pg_trigger
                WHERE tgname = ? AND tgrelid = 'vector_store'::regclass
                """, String.class, TRIGGER_NAME);
        assertThat(triggerExists).isNotNull();

        assertThatThrownBy(() -> {
            List<MarkdownChunk> chunks = List.of(
                    new MarkdownChunk(source, List.of("Section"), 1, "New content to index"));
            store.index(chunks);
        }).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("forced vector store insert failure");

        // Snapshot every relevant row after operation
        List<Map<String, Object>> afterSnapshot = jdbcTemplate.queryForList("""
                SELECT id::text AS id,
                       content,
                       metadata::text AS metadata,
                       embedding::text AS embedding
                FROM vector_store
                WHERE metadata->>'source' = ?
                ORDER BY ordinal(metadata), id
                """, source);

        // Assert exact snapshot equality - rollback succeeded
        assertThat(afterSnapshot).isEqualTo(beforeSnapshot);
    }

    private static int ordinal(Map<String, Object> row) {
        Object value = row.get("metadata");
        if (value instanceof String json) {
            // Simple JSON parsing for our known structure
            int ordinalIdx = json.indexOf("\"ordinal\":");
            if (ordinalIdx >= 0) {
                int start = ordinalIdx + "\"ordinal\":".length();
                int end = start;
                while (end < json.length() && Character.isDigit(json.charAt(end))) {
                    end++;
                }
                if (end > start) {
                    return Integer.parseInt(json.substring(start, end));
                }
            }
        }
        return -1;
    }
}
