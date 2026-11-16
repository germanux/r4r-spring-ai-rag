package com.riansares.r4r.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Routes independent chat circuits. Every call retries the preferred provider, including after recovery. */
public class GenerationRouter implements ChatModel {
    private static final Logger log = LoggerFactory.getLogger(GenerationRouter.class);
    public record Provider(String name, String baseUrl, String model) { }
    public record Target(Provider provider, ChatModel model, BooleanSupplier available) { }
    private final Target preferred;
    private final List<Target> targets;
    private final java.time.Duration budget;
    private final String circuit;
    private final ThreadLocal<Execution> requestExecution = new ThreadLocal<>();
    // Runtime summary only; individual calls always keep their provider in a local variable.
    public record Execution(Provider provider, boolean failed) { }
    private final AtomicReference<Execution> execution = new AtomicReference<>(new Execution(null, false));

    public GenerationRouter(Target preferred, Target fallback) {
        this(List.of(preferred, fallback));
    }

    public GenerationRouter(List<Target> targets) {
        this(targets, java.time.Duration.ofSeconds(180), "generation");
    }
    public GenerationRouter(List<Target> targets, java.time.Duration budget, String circuit) {
        if (targets == null || targets.isEmpty() || budget == null || budget.toMillis() < 2)
            throw new IllegalArgumentException("Providers and positive global budget required");
        this.targets = List.copyOf(targets);
        this.preferred = targets.getFirst();
        this.budget = budget;
        this.circuit = circuit;
    }
    public List<Provider> remoteProviders() {
        return targets.stream().map(Target::provider).filter(p -> "OpenAI".equals(p.name())).toList();
    }
    public void clearRequestExecution() { requestExecution.remove(); }
    public Execution requestExecution() { return requestExecution.get(); }

    public List<Provider> localProviders() {
        return targets.stream().map(Target::provider).filter(p -> !"OpenAI".equals(p.name())).toList();
    }

    public Provider preferred() { return preferred.provider(); }
    public Provider lastUsed() { return execution.get().provider(); }
    public Execution execution() { return execution.get(); }
    public Provider preferredAvailable() {
        for (Target target : targets) {
            if ("OpenAI".equals(target.provider().name())) continue; // Never preload remote models.
            try { if (target.available().getAsBoolean()) return target.provider(); }
            catch (RuntimeException ignored) { }
        }
        throw unavailable();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return route(prompt, RagProgress.silent());
    }

    public ChatResponse call(Prompt prompt, RagProgress progress) { return route(prompt, progress); }

    private ChatResponse route(Prompt prompt, RagProgress progress) {
        return route(prompt, progress, provider -> {});
    }

    ChatResponse route(Prompt prompt, RagProgress progress, java.util.function.Consumer<Provider> attempted) {
        clearRequestExecution();
        String requestId = UUID.randomUUID().toString();
        var ordered = java.util.stream.IntStream.range(0, targets.size())
                .mapToObj(i -> new OrderedProviderFailover.Target<>("provider-" + i, targets.get(i))).toList();
        try {
            return ProviderExecution.within(budget, () -> OrderedProviderFailover.execute(ordered,
                    target -> { progress.checkCancelled(); return true; },
                    target -> attempt(target, prompt, progress, attempted, requestId), ProviderExecution::recoverable,
                    next -> progress.emit("FAILOVER_STARTED", next.client().provider(), null, null, null)).value());
        } catch (OrderedProviderFailover.Exhausted exhausted) {
            failed();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Los proveedores de generación no están disponibles", exhausted);
        } catch (RuntimeException failure) {
            failed();
            throw failure;
        }
    }

    private ChatResponse attempt(Target target, Prompt prompt, RagProgress progress,
                                 java.util.function.Consumer<Provider> attempted, String requestId) {
        progress.checkCancelled();
        Provider provider = target.provider();
        attempted.accept(provider);
        long started = System.nanoTime();
        log.info("Provider circuit={} id={} provider={} host={} model={} outcome=attempt remainingMs={}",
                circuit, requestId, provider.name(), RagProgress.publicProvider(provider), provider.model(), ProviderExecution.remainingMillis());
        try {
            if (!target.available().getAsBoolean()) throw new ProviderExecution.Unavailable();
            progress.checkCancelled();
            progress.emit("GENERATION_STARTED", provider, null, null, null);
            ChatResponse response = java.util.Objects.requireNonNull(target.model().call(prompt), "Provider result");
            progress.checkCancelled();
            ProviderExecution.check();
            progress.usedProvider(provider);
            execution.set(new Execution(provider, false));
            requestExecution.set(new Execution(provider, false));
            log.info("Provider circuit={} id={} provider={} model={} outcome=selected durationMs={}",
                    circuit, requestId, provider.name(), provider.model(), (System.nanoTime() - started) / 1_000_000.0);
            return response;
        } catch (java.util.concurrent.CancellationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            progress.checkCancelled();
            ProviderExecution.checkCancellation(ex);
            progress.emit("PROVIDER_FAILED", provider, null, null, "provider_failed");
            log.warn("Provider circuit={} id={} provider={} model={} outcome=failed reason={} error={} durationMs={}",
                    circuit, requestId, provider.name(), provider.model(), failureReason(ex),
                    ex.getClass().getSimpleName(), (System.nanoTime() - started) / 1_000_000.0);
            throw ex;
        }
    }

    private void failed() {
        execution.set(new Execution(null, true));
        requestExecution.set(new Execution(null, true));
    }

    static String failureReason(Throwable failure) {
        return ProviderExecution.reason(failure);
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Los proveedores de generación no están disponibles");
    }
}
