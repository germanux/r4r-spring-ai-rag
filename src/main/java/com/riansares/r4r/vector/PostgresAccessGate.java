package com.riansares.r4r.vector;

import java.sql.SQLException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import com.riansares.r4r.vector.config.PostgresFailoverProperties;

/** One bounded, on-demand recovery attempt; no HTTP-thread queue or background retry loop. */
public final class PostgresAccessGate {
    private static final Logger log = LoggerFactory.getLogger(PostgresAccessGate.class);
    private final DataSource database;
    private final Supplier<Flyway> migrations;
    private final long interval;
    private final LongSupplier nanoTime;
    private final ReentrantLock maintenance = new ReentrantLock();
    private volatile boolean ready, validated;
    private volatile Backoff backoff;
    private volatile VectorStoreUnavailableException invalidSchema;
    private record Backoff(Throwable failure, long retryAt) { }
    private static final class Unavailable extends CannotGetJdbcConnectionException {
        Unavailable(Throwable cause) { super("PostgreSQL is temporarily unavailable", new IllegalStateException("PostgreSQL is not ready", cause)); }
    }

    public PostgresAccessGate(DataSource database, Supplier<Flyway> migrations, PostgresFailoverProperties properties) {
        this(database, migrations, properties, System::nanoTime);
    }
    public PostgresAccessGate(DataSource database, Supplier<Flyway> migrations,
            PostgresFailoverProperties properties, LongSupplier nanoTime) {
        this.database = database; this.migrations = migrations;
        this.interval = properties.recoveryInterval().toNanos(); this.nanoTime = nanoTime;
    }
    public void migrateAtStartup(Flyway flyway) {
        try { prepare(flyway); }
        catch (Unavailable deferred) { /* Read fallback remains available; writes stay gated. */ }
    }
    public void beforeRead() { prepare(null); }
    public void beforeWrite() {
        prepare(null);
        if (!validated) throw new VectorStoreUnavailableException("Flyway validation is required before writing", null);
    }
    public void recordFailure(RuntimeException failure) {
        if (failure instanceof Unavailable) return; // Do not extend cooldown for rejected/repeated requests.
        String reason = PostgresFailureClassifier.reason(failure);
        if (reason != null) unavailable(failure, reason);
    }
    private synchronized void unavailable(Throwable failure, String reason) {
        long now = nanoTime.getAsLong();
        if (!ready && backoff != null && now - backoff.retryAt() < 0) return;
        ready = false; validated = false;
        backoff = new Backoff(failure, now + interval);
        log.warn("PostgreSQL state=unavailable reason={} retryAfterMs={}", reason, interval / 1_000_000);
    }
    private void checkRetry() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("PostgreSQL access cancelled");
        if (invalidSchema != null) throw invalidSchema;
        Backoff state = backoff;
        if (state != null && nanoTime.getAsLong() - state.retryAt() < 0) throw new Unavailable(state.failure());
    }
    private void prepare(Flyway bootstrap) {
        checkRetry();
        if (ready) return;
        if (!maintenance.tryLock()) throw new Unavailable(null);
        try {
            checkRetry();
            if (ready) return;
            long started = nanoTime.getAsLong();
            boolean recovering = backoff != null;
            try {
                // Probe before migrations: a cold, unreachable database is not a migration failure.
                try (var connection = database.getConnection()) { }
                Flyway flyway = bootstrap != null ? bootstrap : migrations.get();
                if (flyway != null) {
                    flyway.migrate();
                    flyway.validate(); // Required even if validate-on-migrate was explicitly disabled.
                }
                validated = flyway != null;
                backoff = null; ready = true;
                log.info("PostgreSQL state={} migrationsValidated={} durationMs={}",
                        recovering ? "recovered" : "ready", validated, (nanoTime.getAsLong() - started) / 1_000_000.0);
            } catch (SQLException | RuntimeException failure) {
                String reason = PostgresFailureClassifier.reason(failure);
                if (reason != null) {
                    unavailable(failure, reason);
                    throw new Unavailable(failure);
                }
                if (failure instanceof CancellationException cancelled) throw cancelled;
                if (Thread.currentThread().isInterrupted()) throw new CancellationException("PostgreSQL access cancelled");
                invalidSchema = new VectorStoreUnavailableException("PostgreSQL initialization or schema validation failed", failure);
                log.error("PostgreSQL state=invalid initializationFailureType={}", failure.getClass().getSimpleName());
                throw invalidSchema;
            }
        } finally { maintenance.unlock(); }
    }
}
