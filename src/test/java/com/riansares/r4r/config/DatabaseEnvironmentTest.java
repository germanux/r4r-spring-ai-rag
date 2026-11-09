package com.riansares.r4r.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
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
    @org.junit.jupiter.api.io.TempDir Path root;

    @org.junit.jupiter.api.BeforeEach
    void publicConfigurationOnly() throws IOException {
        Path application = root.resolve("application.yml");
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"))
                .replace("      - optional:file:./docker-spring/.env[.properties]\n", "")
                .replace("optional:file:./docker-spring/.env.base[.properties]",
                        Path.of("docker-spring/.env.base").toAbsolutePath().toUri() + "[.properties]");
        Files.writeString(application, yaml + "\nPOSTGRES_PASSWORD: synthetic-test-password\n");
        Files.copy(Path.of("src/test/resources/application-test.yml"), root.resolve("application-test.yml"));
        Files.copy(Path.of("src/test/resources/application.properties"), root.resolve("application.properties"));
    }

    private StandardEnvironment isolatedEnvironment() {
        var env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        return env;
    }
    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration { }

    private StandardEnvironment productionProperties(Map<String, Object> overrides) {
        var env = isolatedEnvironment();
        var safeOverrides = new java.util.HashMap<String, Object>(overrides);
        safeOverrides.put("spring.config.location", root.toUri().toString());
        env.getPropertySources().addFirst(new MapPropertySource("test-overrides", safeOverrides));
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
        assertThat("synthetic-test-password".equals(env.getProperty("spring.datasource.password")))
                .as("Only the synthetic fixture password must be resolved").isTrue();
    }

    @Test
    void mavenProfileUsesOnlySynologyTestDatabase() {
        try (var context = new SpringApplicationBuilder(EmptyConfiguration.class)
                .environment(isolatedEnvironment()).web(WebApplicationType.NONE)
                .run("--spring.config.location=" + root.toUri(), "--spring.profiles.active=test")) {
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
        command.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(StandaloneProbe.class.getName());
        command.add("--spring.config.location=" + root.toUri());
        command.addAll(java.util.List.of(args));
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        var env = builder.environment();
        env.keySet().removeIf(key -> key.startsWith("AWS_") || key.startsWith("RAG_")
                || key.startsWith("SPRING_") || key.startsWith("POSTGRES_") || key.equals("OPENAI_API_KEY")
                || key.equals("JAVA_TOOL_OPTIONS") || key.equals("_JAVA_OPTIONS") || key.equals("JDK_JAVA_OPTIONS"));
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
