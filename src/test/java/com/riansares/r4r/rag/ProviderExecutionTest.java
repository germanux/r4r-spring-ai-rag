package com.riansares.r4r.rag;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import static org.assertj.core.api.Assertions.*;

class ProviderExecutionTest {
    @Test void cancellationStopsEvenWhenCallerClassifiesEverythingAsRecoverable() {
        var calls = new AtomicInteger();
        var cancelled = new CancellationException("cancelled");
        assertThatThrownBy(() -> OrderedProviderFailover.execute(
                List.of(new OrderedProviderFailover.Target<>("a", "a"), new OrderedProviderFailover.Target<>("b", "b")),
                t -> true, t -> { calls.incrementAndGet(); throw cancelled; }, e -> true, t -> {}))
                .isSameAs(cancelled);
        assertThat(calls).hasValue(1);
    }

    @Test void probeFailuresParticipateInTheSameFailoverAndPreserveTheirCause() {
        var cause = new ResourceAccessException("offline", new java.net.ConnectException());
        assertThatThrownBy(() -> OrderedProviderFailover.execute(
                List.of(new OrderedProviderFailover.Target<>("a", "a")),
                t -> { throw cause; }, t -> t, ProviderExecution::recoverable, t -> {}))
                .isInstanceOf(OrderedProviderFailover.Exhausted.class).hasCause(cause);
    }

    @Test void nestedBudgetsCannotExtendDeadlineAndScopeIsRestored() {
        ProviderExecution.within(Duration.ofMillis(100), () -> {
            long outer = ProviderExecution.remainingMillis();
            ProviderExecution.within(Duration.ofSeconds(2), () -> {
                assertThat(ProviderExecution.remainingMillis()).isLessThanOrEqualTo(outer);
                return true;
            });
            return true;
        });
        assertThat(ProviderExecution.remainingMillis()).isEqualTo(Long.MAX_VALUE);
    }

    @Test void exhaustedGlobalBudgetNeverStartsAnotherProvider() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> ProviderExecution.within(Duration.ofMillis(20), () -> OrderedProviderFailover.execute(
                List.of(new OrderedProviderFailover.Target<>("a", "a"), new OrderedProviderFailover.Target<>("b", "b")),
                t -> true, t -> {
                    calls.incrementAndGet();
                    try { Thread.sleep(30); } catch (InterruptedException e) { throw new CancellationException(); }
                    throw new ProviderExecution.Unavailable();
                }, ProviderExecution::recoverable, t -> {})))
                .isInstanceOf(ProviderExecution.BudgetExpired.class).hasCauseInstanceOf(ProviderExecution.Unavailable.class);
        assertThat(calls).hasValue(1);
    }

    @Test void interruptionIsPreservedAndTlsConfigurationErrorsAreNeverRecoverable() {
        try {
            assertThatThrownBy(() -> ProviderExecution.checkCancellation(new IllegalStateException(new InterruptedException())))
                    .isInstanceOf(CancellationException.class).hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        assertThat(ProviderExecution.recoverable(new ResourceAccessException("tls",
                new java.net.SocketException() {{ initCause(new javax.net.ssl.SSLException("tls")); }}))).isFalse();
        assertThat(ProviderExecution.recoverable(new IllegalArgumentException("invalid"))).isFalse();
    }
}
