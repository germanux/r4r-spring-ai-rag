package com.riansares.r4r.rag;

import com.riansares.r4r.config.RagRetrievalProperties;
import com.riansares.r4r.config.GenerationProviderProperties;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RagRetrievalProperties.class, OllamaChatProperties.class, GenerationProviderProperties.class, com.riansares.r4r.config.AuditProviderProperties.class})
public class RagConfiguration {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RagConfiguration.class);

    @Value("${spring.ai.ollama.chat.think:false}")
    private boolean think;

    @Bean
    OpenAiResponsesModel sharedOpenAiClient(GenerationProviderProperties generation,
            com.riansares.r4r.config.AuditProviderProperties audit,
            @Value("${r4r.rag.openai.max-calls-per-day:20}") int limit,
            @Value("${OPENAI_API_KEY:}") String key) {
        boolean remote = java.util.stream.Stream.concat(generation.providers().stream(), audit.providers().stream())
                .anyMatch(GenerationProviderProperties.Provider::remote);
        if (remote && key.isBlank()) throw new IllegalStateException("OPENAI_API_KEY required when a provider list contains openai");
        return new OpenAiResponsesModel(RestClient.builder().baseUrl(com.riansares.r4r.config.ProviderList.OPENAI_URL)
                .requestFactory(ProviderExecution.http(Duration.ofSeconds(3), Duration.ofMinutes(3))).build(), key, "configured-per-entry", limit);
    }

    @Bean
    List<GenerationRouter.Target> generationTargets(OllamaChatProperties properties,
            GenerationProviderProperties generation, OpenAiResponsesModel remote) {
        return generation.providers().stream().map(p -> configuredTarget(p, properties.getOptions(),
                generation.connectTimeout(), generation.probeTimeout(), remote, "8h")).toList();
    }

    @Bean
    @org.springframework.context.annotation.Primary
    GenerationRouter generationRouter(@Qualifier("generationTargets") List<GenerationRouter.Target> targets,
            @Value("${r4r.rag.generation.total-timeout:180s}") Duration budget) {
        return new GenerationRouter(targets, budget, "generation");
    }

    private GenerationRouter.Target configuredTarget(GenerationProviderProperties.Provider p, OllamaOptions defaults,
            Duration connect, Duration probe, OpenAiResponsesModel remote, String keepAlive) {
        if (p.model() == null || p.model().isBlank() || p.readTimeout() == null || p.readTimeout().toMillis() < 2)
            throw new IllegalArgumentException("Provider model and positive timeout required");
        log.info("Configured chat provider={} model={} connectTimeoutMs={} probeTimeoutMs={} readTimeoutMs={}",
                p.name(), p.model(), p.remote() ? 3000 : connect.toMillis(), p.remote() ? 0 : probe.toMillis(), p.readTimeout().toMillis());
        if (p.remote()) return new GenerationRouter.Target(
                new GenerationRouter.Provider("OpenAI", com.riansares.r4r.config.ProviderList.OPENAI_URL, p.model()),
                prompt -> ProviderExecution.within(p.readTimeout().plusSeconds(3), () -> remote.forModel(p.model()).call(prompt)), () -> true);
        return target(p.name(), p.baseUrl(), p.model(), defaults, connect, probe, p.readTimeout(), keepAlive);
    }

    private GenerationRouter.Target target(String name, String url, String model, OllamaOptions defaults,
                                           Duration connectTimeout, Duration probeTimeout, Duration readTimeout, String keepAlive) {
        var generationHttp = ProviderExecution.http(connectTimeout, readTimeout);
        OllamaApi api = OllamaApi.builder().baseUrl(url)
                .responseErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler())
                .restClientBuilder(RestClient.builder().requestFactory(generationHttp)
                        .requestInterceptor(new OllamaThinkingInterceptor(think))).build();
        var probeHttp = ProviderExecution.http(connectTimeout, probeTimeout);
        OllamaApi probe = OllamaApi.builder().baseUrl(url)
                .responseErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler())
                .restClientBuilder(RestClient.builder().requestFactory(probeHttp)).build();
        OllamaOptions options = OllamaOptions.fromOptions(defaults);
        options.setModel(model);
        options.setKeepAlive(keepAlive);
        var chat = OllamaChatModel.builder().ollamaApi(api).defaultOptions(options)
                .retryTemplate(org.springframework.retry.support.RetryTemplate.builder().maxAttempts(1).build()).build();
        return new GenerationRouter.Target(new GenerationRouter.Provider(name, url, model),
                prompt -> ProviderExecution.within(readTimeout.plus(connectTimeout), () -> chat.call(prompt)),
                () -> probe.listModels().models().stream().anyMatch(m -> canonical(model).equals(canonical(m.name()))));
    }

    private static String canonical(String name) { return name.contains(":") ? name : name + ":latest"; }

    @Bean
    RagAuditService ragAuditService(com.riansares.r4r.config.AuditProviderProperties audit,
            GenerationProviderProperties generation, OllamaChatProperties properties, OpenAiResponsesModel remote,
            org.springframework.beans.factory.ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapper) {
        var targets = audit.providers().stream().map(p -> {
            boolean shared = generation.providers().stream().anyMatch(g -> !g.remote()
                    && g.baseUrl().equals(p.baseUrl()) && canonical(g.model()).equals(canonical(p.model())));
            var target = configuredTarget(p, properties.getOptions(),audit.connectTimeout(), audit.probeTimeout(), remote, shared ? "8h" : "0s");
            return p.remote() ? target : new GenerationRouter.Target(
                    new GenerationRouter.Provider("Ollama", p.baseUrl(), p.model()),target.model(), target.available());
        }).toList();
        return new RagAuditService(targets, audit.totalTimeout(), mapper.getIfAvailable(() -> new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()));
    }

    @Bean
    CitedRagService citedRagService(
            KnowledgeVectorStore knowledgeStore,
            GenerationRouter chatModel,
            RagRetrievalProperties properties,
            @Value("${r4r.rag.evidence-relevance.enabled:false}") boolean relevanceEnabled,
            @Value("${r4r.rag.evaluation.capture-directory:}") String captureDirectory,
            org.springframework.beans.factory.ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> json) {

        return new CitedRagService(
                knowledgeStore,
                chatModel,
                properties.topK(),
                properties.minScore(), relevanceEnabled ? new EvidenceRelevanceFilter(chatModel) : null,
                captureDirectory.isBlank() ? null : new RagEvidenceExporter(java.nio.file.Path.of(captureDirectory),
                        json.getIfAvailable(() -> new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules())));
    }
}
