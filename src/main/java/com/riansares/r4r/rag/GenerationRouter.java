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

/** Routes generation only. Every call retries the preferred provider, including after recovery. */
public class GenerationRouter implements ChatModel {
    private static final Logger log = LoggerFactory.getLogger(GenerationRouter.class);
    public record Provider(String name, String baseUrl, String model) { }
    public record Target(Provider provider, ChatModel model, BooleanSupplier available) { }
    private final Target preferred;
    private final List<Target> targets;
    private final ThreadLocal<Execution> requestExecution = new ThreadLocal<>();
    // Runtime summary only; individual calls always keep their provider in a local variable.
    public record Execution(Provider provider, boolean failed) { }
    private final AtomicReference<Execution> execution = new AtomicReference<>(new Execution(null, false));

    public GenerationRouter(Target preferred, Target fallback) {
        this(List.of(preferred, fallback));
    }

    public GenerationRouter(List<Target> targets) {
        this.targets = List.copyOf(targets);
        this.preferred = targets.getFirst();
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
        return route(prompt, false);
    }

    /** Avoid retrying known-failed local hosts after remote translation in the same request. */
    public ChatResponse callRemoteOnly(Prompt prompt) {
        return route(prompt, true);
    }

    private ChatResponse route(Prompt prompt, boolean remoteOnly) {
        clearRequestExecution();
        String requestId = UUID.randomUUID().toString();
        for (Target target : targets) {
            Provider provider = target.provider();
            if (remoteOnly && !"OpenAI".equals(provider.name())) continue;
            try {
                if (!target.available().getAsBoolean()) continue;
                ChatResponse response = target.model().call(prompt);
                execution.set(new Execution(provider, false));
                requestExecution.set(new Execution(provider, false));
                log.info("Generation {} completed by provider={} model={}", requestId, provider.name(), provider.model());
                return response;
            } catch (RuntimeException ex) {
                log.warn("Generation {} failed at provider={} model={} error={}", requestId,
                        provider.name(), provider.model(), ex.getClass().getSimpleName());
            }
        }
        execution.set(new Execution(null, true));
        requestExecution.set(new Execution(null, true));
        throw unavailable();
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Los proveedores de generación no están disponibles");
    }
}
