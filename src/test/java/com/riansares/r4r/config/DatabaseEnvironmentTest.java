package com.riansares.r4r.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseEnvironmentTest {
    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration { }

    private StandardEnvironment productionProperties(Map<String, Object> overrides) {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test-overrides", overrides));
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        return env;
    }

    @Test
    void localApplicationUsesLanProductionDatabase() {
        var env = productionProperties(Map.of(
                "spring.config.location", "file:src/main/resources/application.yml",
                "spring.profiles.active", "default"));
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo(
                "jdbc:postgresql://" + env.getProperty("POSTGRES_HOST") + ":"
                        + env.getProperty("POSTGRES_PORT") + "/" + env.getProperty("POSTGRES_DB"));
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo(env.getProperty("POSTGRES_PASSWORD"));
    }

    @Test
    void mavenProfileUsesOnlySynologyTestDatabase() {
        try (var context = new SpringApplicationBuilder(EmptyConfiguration.class)
                .web(WebApplicationType.NONE).run("--spring.profiles.active=test")) {
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://192.168.1.203:5433/r4r_rag_test");
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("r4r_test");
        }
    }

    @Test
    void dockerDatasourceOverrideTakesPrecedence() {
        var env = productionProperties(Map.of(
                "spring.config.location", "file:src/main/resources/application.yml",
                "spring.datasource.url", "jdbc:postgresql://postgres:5432/r4r_rag"));
        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://postgres:5432/r4r_rag");
    }

    /** Starts without Maven system properties, as an IDE or child JVM does. */
    public static class StandaloneProbe {
        public static void main(String[] args) {
            try (var context = new SpringApplicationBuilder(EmptyConfiguration.class)
                    .web(WebApplicationType.NONE).run(args)) {
                System.out.println("TEST_DATABASE_GUARD_OK");
            }
        }
    }

    private ProcessResult runStandalone(boolean missingDatabase, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(StandaloneProbe.class.getName());
        command.addAll(java.util.List.of(args));
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        var env = builder.environment();
        env.remove("SPRING_PROFILES_ACTIVE");
        env.remove("SPRING_DATASOURCE_URL");
        env.remove("SPRING_DATASOURCE_USERNAME");
        env.remove("SPRING_FLYWAY_URL");
        env.put("POSTGRES_HOST", "192.168.1.203");
        env.put("POSTGRES_TEST_PORT", "5433");
        env.put("POSTGRES_TEST_DB", missingDatabase ? "" : "r4r_rag_test");
        env.put("POSTGRES_TEST_USER", "r4r_test");
        env.put("POSTGRES_PASSWORD", "synthetic-test-password");
        var process = builder.start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Standalone Spring test exceeded 30 seconds");
        }
        return new ProcessResult(process.exitValue(),
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private record ProcessResult(int exitCode, String output) { }

    @Test
    void standaloneLaunchDefaultsToTestAndRejectsUnsafeOverrides() throws Exception {
        var valid = runStandalone(false);
        assertThat(valid.exitCode()).isZero();
        assertThat(valid.output()).contains("TEST_DATABASE_GUARD_OK");
        assertRejected(runStandalone(false, "--spring.profiles.active=default"), "Tests require the Spring test profile");
        assertRejected(runStandalone(false, "--spring.datasource.url=jdbc:postgresql://192.168.1.203:5432/r4r_rag"), "spring.datasource.url");
        assertRejected(runStandalone(false, "--spring.flyway.url=jdbc:postgresql://192.168.1.203:5432/r4r_rag"), "spring.flyway.url");
        assertRejected(runStandalone(true), "spring.datasource.url");
    }

    private void assertRejected(ProcessResult result, String reason) {
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains(reason).doesNotContain("TEST_DATABASE_GUARD_OK");
    }

    @Test
    void guardRejectsProductionOverridesAndMissingTestConfiguration() {
        var env = new MockEnvironment();
        env.setActiveProfiles("test");
        assertThatThrownBy(() -> TestDatabaseGuard.validate(env)).isInstanceOf(IllegalStateException.class);
        env.setProperty("spring.datasource.url", "jdbc:postgresql://192.168.1.203:5433/r4r_rag_test");
        env.setProperty("spring.datasource.username", "r4r_test");
        TestDatabaseGuard.validate(env);
        env.setProperty("spring.flyway.url", "jdbc:postgresql://192.168.1.203:5432/r4r_rag");
        assertThatThrownBy(() -> TestDatabaseGuard.validate(env)).isInstanceOf(IllegalStateException.class);
        env.setProperty("spring.flyway.url", "jdbc:postgresql://192.168.1.203:5433/r4r_rag_test");
        env.setProperty("spring.datasource.hikari.jdbc-url", "jdbc:postgresql://192.168.1.203:5432/r4r_rag");
        assertThatThrownBy(() -> TestDatabaseGuard.validate(env)).isInstanceOf(IllegalStateException.class);
        env.setProperty("spring.datasource.hikari.jdbc-url", "jdbc:postgresql://192.168.1.203:5433/r4r_rag_test");
        env.setProperty("spring.datasource.url", "jdbc:postgresql://192.168.1.203:5432/r4r_rag");
        assertThatThrownBy(() -> TestDatabaseGuard.validate(env)).isInstanceOf(IllegalStateException.class);
        env.setActiveProfiles("default");
        assertThatThrownBy(() -> TestDatabaseGuard.validate(env)).isInstanceOf(IllegalStateException.class);
    }
}
