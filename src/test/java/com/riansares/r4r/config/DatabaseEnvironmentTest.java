package com.riansares.r4r.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseEnvironmentTest {
    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration { }

    @Test
    void localApplicationUsesLanProductionDatabase() {
        try (var context = new SpringApplicationBuilder(EmptyConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.location=file:src/main/resources/application.yml",
                        "--spring.profiles.active=default")) {
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.url")).isEqualTo(
                    "jdbc:postgresql://" + env.getProperty("POSTGRES_HOST") + ":"
                            + env.getProperty("POSTGRES_PORT") + "/" + env.getProperty("POSTGRES_DB"));
            assertThat(env.getProperty("spring.datasource.password"))
                    .isEqualTo(env.getProperty("POSTGRES_PASSWORD"));
        }
    }

    @Test
    void mavenProfileUsesOnlySynologyTestDatabase() {
        try (var context = new SpringApplicationBuilder(EmptyConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.profiles.active=test")) {
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://192.168.1.203:5433/r4r_rag_test");
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("r4r_test");
        }
    }

    @Test
    void dockerDatasourceOverrideTakesPrecedence() {
        try (var context = new SpringApplicationBuilder(EmptyConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.location=file:src/main/resources/application.yml",
                        "--spring.datasource.url=jdbc:postgresql://postgres:5432/r4r_rag")) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://postgres:5432/r4r_rag");
        }
    }
}
