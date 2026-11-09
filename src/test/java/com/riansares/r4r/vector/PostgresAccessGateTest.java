package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.config.PostgresFailoverProperties;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostgresAccessGateTest {
    final DataSource db = mock(DataSource.class);
    final Flyway flyway = mock(Flyway.class);
    final AtomicBoolean up = new AtomicBoolean();
    final AtomicLong time = new AtomicLong();
    final PostgresFailoverProperties properties = new PostgresFailoverProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1));
    final PostgresAccessGate gate = new PostgresAccessGate(db, () -> flyway, properties, time::get);
    final KnowledgeVectorStore pg = mock(KnowledgeVectorStore.class), aws = mock(KnowledgeVectorStore.class);
    final List<MarkdownChunk> chunks = List.of(new MarkdownChunk("faq.md", List.of("FAQ"), 2, "Reformas documentadas."));
    @BeforeEach void database() throws Exception {
        when(db.getConnection()).thenAnswer(invocation -> {
            if (!up.get()) throw new SQLException("private-host / synthetic credential", "08001", new java.net.UnknownHostException("postgres"));
            return mock(Connection.class);
        });
        when(pg.search("q", 5, .63)).thenReturn(chunks);
        when(aws.search("q", 5, .63)).thenReturn(chunks);
    }
    @AfterEach void clearInterrupt() { Thread.interrupted(); }
    FailoverKnowledgeVectorStore router() { return new FailoverKnowledgeVectorStore(() -> pg, aws, "pgvector", true, false, gate); }
    @Test void coldStartupUsesAwsWithoutRepeatedConnectionAttemptsAndRecovers() throws Exception {
        gate.migrateAtStartup(flyway);
        var router = router();
        for (int i = 0; i < 10; i++) assertThat(router.search("q", 5, .63)).isEqualTo(chunks);
        verify(db).getConnection(); verifyNoInteractions(pg, flyway);
        up.set(true); time.set(Duration.ofSeconds(2).toNanos());
        assertThat(router.search("q", 5, .63)).isEqualTo(chunks);
        var order = inOrder(flyway, pg);
        order.verify(flyway).migrate(); order.verify(flyway).validate(); order.verify(pg).search("q", 5, .63);
        verify(aws, times(10)).search("q", 5, .63);
        gate.beforeWrite(); verify(db, times(2)).getConnection();
    }
    @Test void jdbcDnsFailureWithoutSqlStateStillDefersButBareDnsFailureIsNotPostgresEvidence() throws Exception {
        doThrow(new SQLException("unavailable", new java.net.UnknownHostException("postgres"))).when(db).getConnection();
        gate.migrateAtStartup(flyway);
        assertThat(router().search("q", 5, .63)).isEqualTo(chunks);
        assertThat(PostgresFailureClassifier.reason(new java.net.UnknownHostException("embedding-host"))).isNull();
        verifyNoInteractions(pg, flyway);
    }
    @Test void warmOutageTriggersCooldownButEmptyResultsAndHealthyReadsNeverUseAws() {
        up.set(true); gate.migrateAtStartup(flyway);
        var router = router(); router.search("q", 5, .63); verifyNoInteractions(aws);
        when(pg.search("q", 5, .63)).thenThrow(new CannotGetJdbcConnectionException("down"));
        router.search("q", 5, .63); router.search("q", 5, .63);
        verify(pg, times(2)).search("q", 5, .63);
        doReturn(List.of()).when(pg).search("q", 5, .63);
        time.set(Duration.ofSeconds(2).toNanos());
        assertThat(router.search("q", 5, .63)).isEmpty();
        verify(aws, times(2)).search("q", 5, .63); verify(flyway, times(2)).validate();
    }
    @ParameterizedTest @ValueSource(strings = {"28P01", "42P01", "22000", "57014"})
    void permanentFailuresAreNotDeferredOrRetried(String state) throws Exception {
        doThrow(new SQLException("do not disclose", state)).when(db).getConnection();
        assertThatThrownBy(() -> gate.migrateAtStartup(flyway)).isInstanceOf(VectorStoreUnavailableException.class);
        time.set(Duration.ofMinutes(10).toNanos());
        assertThatThrownBy(gate::beforeWrite).isInstanceOf(VectorStoreUnavailableException.class);
        verify(db).getConnection(); verifyNoInteractions(flyway);
    }
    @Test void migrationAndValidationErrorsBlockAccessInsteadOfBeingAnOutage() {
        up.set(true); doThrow(new IllegalStateException("checksum mismatch")).when(flyway).validate();
        assertThatThrownBy(() -> gate.migrateAtStartup(flyway)).isInstanceOf(VectorStoreUnavailableException.class);
        assertThatThrownBy(() -> router().search("q", 5, .63)).isInstanceOf(VectorStoreUnavailableException.class);
        verifyNoInteractions(pg, aws);
    }
    @Test void disabledMigrationsAllowReadChecksButNeverWritesInDegradedMode() {
        up.set(true);
        var readOnly = new PostgresAccessGate(db, () -> null, properties, time::get);
        readOnly.beforeRead();
        var router = new FailoverKnowledgeVectorStore(() -> pg, aws, "pgvector", true, false, readOnly);
        assertThatThrownBy(() -> router.index(chunks)).isInstanceOf(VectorStoreUnavailableException.class);
        verifyNoInteractions(pg, aws, flyway);
    }
    @Test void concurrentRequestsNeverQueueBehindMigrationOrStartDuplicates() throws Exception {
        up.set(true); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(flyway.migrate()).thenAnswer(invocation -> { entered.countDown(); release.await(5, TimeUnit.SECONDS); return null; });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(gate::beforeRead);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            try {
                var second = executor.submit(() -> assertThatThrownBy(gate::beforeRead).isInstanceOf(CannotGetJdbcConnectionException.class));
                second.get(1, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            first.get(2, TimeUnit.SECONDS);
        }
        verify(flyway).migrate(); verify(flyway).validate();
    }
    @Test void interruptionDoesNotPoisonFutureReadinessOrTriggerFallback() {
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> router().search("q", 5, .63)).isInstanceOf(CancellationException.class);
        verifyNoInteractions(pg, aws, db); Thread.interrupted();
        up.set(true); gate.beforeWrite();
    }
    @Test void diagnosticsContainTransitionsButNotSensitiveFailureDetails() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PostgresAccessGate.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            gate.migrateAtStartup(flyway); up.set(true); time.set(Duration.ofSeconds(2).toNanos()); gate.beforeRead();
            var messages = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).anyMatch(m -> m.contains("state=unavailable reason=sqlstate_08001"));
            assertThat(messages).anyMatch(m -> m.contains("state=recovered") && m.contains("durationMs="));
            assertThat(messages).noneMatch(m -> m.contains("private-host") || m.contains("credential"));
        } finally { logger.detachAppender(appender); }
    }
}
