package com.riansares.r4r.rag;

import java.io.EOFException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientResponseException;

/** Small shared execution policy; adapters retain their own inference and quality checks. */
public final class ProviderExecution {
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private ProviderExecution() { }
    public static final class Unavailable extends RuntimeException {
        public Unavailable() { super("Provider unavailable"); }
    }
    public static final class BudgetExpired extends RuntimeException {
        public BudgetExpired() { super("Provider time budget exhausted"); }
    }
    public static <R> R within(Duration budget, Supplier<R> action) {
        return until(System.nanoTime() + budget.toNanos(), action);
    }
    public static <R> R until(long deadline, Supplier<R> action) {
        Long previous = DEADLINE.get();
        DEADLINE.set(previous == null ? deadline : Math.min(previous, deadline));
        try { check(); R result = action.get(); check(); return result; }
        finally { if (previous == null) DEADLINE.remove(); else DEADLINE.set(previous); }
    }
    public static long remainingMillis() {
        Long end = DEADLINE.get();
        return end == null ? Long.MAX_VALUE : Math.max(0, (end - System.nanoTime()) / 1_000_000);
    }
    public static void check() {
        checkCancellation(null);
        if (remainingMillis() < 2) throw new BudgetExpired();
    }
    public static void checkCancellation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof CancellationException cancelled) throw cancelled;
            if (cause instanceof InterruptedException || cause instanceof java.nio.channels.ClosedByInterruptException
                    || cause instanceof InterruptedIOException && !(cause instanceof SocketTimeoutException)) {
                Thread.currentThread().interrupt();
                var cancelled = new CancellationException("Provider call cancelled");
                cancelled.initCause(cause); throw cancelled;
            }
        }
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Provider call cancelled");
    }
    public static ClientHttpRequestFactory http(Duration connect, Duration read) {
        return (uri, method) -> {
            check();
            long remaining = remainingMillis();
            long connection = Math.min(connect.toMillis(), remaining / 2);
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(Math.max(1, connection)));
            factory.setReadTimeout(Duration.ofMillis(Math.max(1, Math.min(read.toMillis(), remaining - connection))));
            return factory.createRequest(uri, method);
        };
    }
    public static boolean recoverable(RuntimeException failure) {
        checkCancellation(failure);
        if (failure instanceof OrderedProviderFailover.Exhausted exhausted)
            return exhausted.getCause() instanceof RuntimeException cause && recoverable(cause);
        if (failure instanceof OpenAiResponsesModel.RemoteFailure remote)
            return !java.util.Set.of("authentication", "invalid_request", "non_recoverable").contains(remote.reason());
        if (failure instanceof Unavailable || failure instanceof BudgetExpired) return true;
        if (failure instanceof RestClientResponseException http)
            return java.util.Set.of(408,429,500,502,503,504).contains(http.getStatusCode().value());
        boolean transport = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException) return false;
            transport |= cause instanceof SocketException || cause instanceof EOFException
                    || cause instanceof java.net.UnknownHostException || cause instanceof java.net.http.HttpTimeoutException
                    || cause instanceof java.util.concurrent.TimeoutException || cause instanceof SocketTimeoutException;
        }
        return transport;
    }
    public static String reason(Throwable failure) {
        if (failure instanceof OpenAiResponsesModel.RemoteFailure remote) return remote.reason();
        if (failure instanceof OrderedProviderFailover.Exhausted exhausted) return reason(exhausted.getCause());
        if (failure instanceof RestClientResponseException http) {
            int status = http.getStatusCode().value();
            if (status == 401 || status == 403) return "authentication";
            if (status == 429) return "saturated";
            if (status == 408) return "timeout";
            if (!java.util.Set.of(500,502,503,504).contains(status)) return "invalid_request";
            return "provider_failed";
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OpenAiResponsesModel.RemoteFailure remote) return remote.reason();
            if (cause instanceof RestClientResponseException http) return reason(http);
            if (cause instanceof BudgetExpired || cause instanceof SocketTimeoutException
                    || cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.util.concurrent.TimeoutException) return "timeout";
            if (cause instanceof CancellationException || cause instanceof InterruptedException) return "cancelled";
            if (cause instanceof java.net.ConnectException || cause instanceof java.net.NoRouteToHostException || cause instanceof Unavailable) return "provider_unavailable";
        }
        return "provider_failed";
    }
}
