package com.riansares.r4r.rag;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Stateless, ordered failover control flow. Domain-specific routers own time budgets,
 * cooldowns, eligibility, protocol adapters and quality validation.
 *
 * An unsuccessful provider attempt is retried on the next provider only if the
 * caller's failure policy explicitly classifies that exception as recoverable.
 */
public final class OrderedProviderFailover {
    private OrderedProviderFailover() { }

    public record Target<T>(String id, T client) {
        public Target {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Provider id is required");
            Objects.requireNonNull(client, "client");
        }
    }

    public record Result<R>(String providerId, R value) { }

    @FunctionalInterface
    public interface Invocation<T, R> {
        R invoke(T client);
    }

    /** Thrown when no provider could produce a result; preserves the final cause. */
    public static final class Exhausted extends RuntimeException {
        public Exhausted(Throwable cause) { super("All eligible providers failed", cause); }
    }

    public static <T, R> Result<R> execute(
            List<Target<T>> targets,
            Predicate<Target<T>> eligible,
            Invocation<T, R> invoke,
            Predicate<RuntimeException> recoverable,
            Consumer<Target<T>> onFallback) {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(eligible, "eligible");
        Objects.requireNonNull(invoke, "invoke");
        Objects.requireNonNull(recoverable, "recoverable");
        Objects.requireNonNull(onFallback, "onFallback");
        if (targets.isEmpty()) throw new IllegalArgumentException("At least one provider is required");
        RuntimeException last = null;
        boolean failed = false;
        for (Target<T> target : targets) {
            try { ProviderExecution.check(); }
            catch (ProviderExecution.BudgetExpired expired) {
                if (last != null) expired.initCause(last);
                throw expired;
            }
            try {
                if (!eligible.test(target)) continue;
                if (failed) onFallback.accept(target);
                R value = Objects.requireNonNull(invoke.invoke(target.client()), "Provider result");
                ProviderExecution.check();
                return new Result<>(target.id(), value);
            } catch (RuntimeException exception) {
                ProviderExecution.checkCancellation(exception);
                if (!recoverable.test(exception)) throw exception;
                last = exception;
                failed = true;
            }
        }
        throw new Exhausted(last);
    }
}
