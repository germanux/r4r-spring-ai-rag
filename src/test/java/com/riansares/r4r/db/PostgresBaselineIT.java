package com.riansares.r4r.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresBaselineIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectsToTheDedicatedTestDatabaseAndFlywayCreatesTheBaseline() {
        String database = jdbcTemplate.queryForObject("select current_database()", String.class);
        String vectorVersion = jdbcTemplate.queryForObject(
                "select extversion from pg_extension where extname = 'vector'", String.class);
        Integer markerCount = jdbcTemplate.queryForObject(
                "select count(*) from r4r_schema_marker where id = 1", Integer.class);

        assertThat(database).isEqualTo("r4r_rag_test");
        assertThat(vectorVersion).isNotBlank();
        assertThat(markerCount).isEqualTo(1);
    }
}
