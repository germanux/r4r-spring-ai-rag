package com.riansares.r4r.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Prevent any test Spring context from connecting to a non-test database. */
public final class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    /** Fail closed if a database is accidentally requested by the isolated local security gate. */
    public static final class NoExternalJdbcDriver extends org.postgresql.Driver {
        @Override public java.sql.Connection connect(String url, java.util.Properties properties) throws java.sql.SQLException {
            throw new java.sql.SQLException("Database access is disabled in isolated local tests");
        }
    }

    private static final String URL = "jdbc:postgresql://192.168.1.203:5433/r4r_rag_test";
    private static final String USER = "r4r_test";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment());
        // Check again immediately before bean creation, after test property customizers.
        context.addBeanFactoryPostProcessor(beanFactory -> validate(context.getEnvironment()));
    }

    static void validate(Environment env) {
        if (!env.matchesProfiles("test")) {
            throw new IllegalStateException("Tests require the Spring test profile");
        }
        require(env, "spring.datasource.url", URL, true);
        require(env, "spring.datasource.username", USER, true);
        require(env, "spring.datasource.hikari.jdbc-url", URL, false);
        require(env, "spring.datasource.hikari.username", USER, false);
        require(env, "spring.flyway.url", URL, false);
        require(env, "spring.flyway.user", USER, false);
        if ("org.postgresql.Driver".equals(env.getProperty("spring.datasource.driver-class-name"))) {
            if (!Boolean.getBoolean("r4r.test.postgres.enabled"))
                throw new IllegalStateException("Real JDBC requires explicit -Ppostgres-it selection");
            if (env.getProperty("R4R_TEST_POSTGRES_PASSWORD", "").isBlank())
                throw new IllegalStateException("R4R_TEST_POSTGRES_PASSWORD is required for -Ppostgres-it");
        }
    }

    private static void require(Environment env, String key, String expected, boolean required) {
        String value = env.getProperty(key);
        if ((required || value != null) && !expected.equals(value)) {
            throw new IllegalStateException("Unsafe or missing test database property: " + key);
        }
    }
}
