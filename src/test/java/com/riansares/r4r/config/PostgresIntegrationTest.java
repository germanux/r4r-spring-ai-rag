package com.riansares.r4r.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.TestPropertySource;

/** Explicit opt-in; the existing database guard still enforces the dedicated test DB. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("postgres")
@EnabledIfSystemProperty(named = "r4r.test.postgres.enabled", matches = "true",
        disabledReason = "NOT RUN: real PostgreSQL requires -Ppostgres-it and R4R_TEST_POSTGRES_PASSWORD")
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.password=${R4R_TEST_POSTGRES_PASSWORD}",
        "spring.flyway.enabled=true" })
public @interface PostgresIntegrationTest { }
