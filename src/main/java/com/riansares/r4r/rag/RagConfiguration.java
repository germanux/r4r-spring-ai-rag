package com.riansares.r4r.rag;

import com.riansares.r4r.config.RagRetrievalProperties;
import com.riansares.r4r.config.GenerationProviderProperties;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RagRetrievalProperties.class, OllamaChatProperties.class, GenerationProviderProperties.class})
public class RagConfiguration {

    @Value("${spring.ai.ollama.chat.think:false}")
    private boolean think;

    @Bean
    List<GenerationRouter.Target> generationTargets(OllamaChatProperties properties,
            GenerationProviderProperties local,
            @Value("${r4r.rag.openai.enabled:false}") String remoteEnabled,
            @Value("${r4r.rag.openai.model:gpt-6-luna}") String remoteModel,
            @Value("${r4r.rag.openai.read-timeout:35s}") Duration remoteReadTimeout,
            @Value("${r4r.rag.openai.max-calls-per-day:20}") int remoteDailyCallLimit,
            @Value("${OPENAI_API_KEY:}") String apiKey) {
        var mode = OpenAiMode.parse(remoteEnabled);
        if (local.providers()==null || local.providers().isEmpty())
            throw new IllegalArgumentException("At least one local generation provider required");
        var targets = new java.util.ArrayList<GenerationRouter.Target>();
        for (var provider:local.providers()) targets.add(target(provider.name(), provider.baseUrl(), provider.model(),
                properties.getOptions(),local.connectTimeout(),local.probeTimeout(),provider.readTimeout()));
        if (mode != OpenAiMode.DISABLED) {
            if (apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY required when remote generation is enabled");
            SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
            http.setConnectTimeout(Duration.ofSeconds(3));
            http.setReadTimeout(remoteReadTimeout);
            var remote = new OpenAiResponsesModel(RestClient.builder().baseUrl("https://api.openai.com/v1")
                    .requestFactory(http).build(), apiKey, remoteModel, remoteDailyCallLimit);
            var target = new GenerationRouter.Target(new GenerationRouter.Provider("OpenAI", "https://api.openai.com/v1", remoteModel), remote, () -> true);
            if (mode == OpenAiMode.PRIMARY) targets.addFirst(target);
            else targets.add(target);
        }
        return List.copyOf(targets);
    }

    @Bean
    GenerationRouter generationRouter(@Qualifier("generationTargets") List<GenerationRouter.Target> targets) {
        return new GenerationRouter(targets);
    }

    private GenerationRouter.Target target(String name, String url, String model, OllamaOptions defaults,
                                           Duration connectTimeout, Duration probeTimeout, Duration readTimeout) {
        return target(name, url, model, defaults, connectTimeout, probeTimeout, readTimeout, "3h");
    }
    private GenerationRouter.Target target(String name, String url, String model, OllamaOptions defaults,
                                           Duration connectTimeout, Duration probeTimeout, Duration readTimeout, String keepAlive) {
        SimpleClientHttpRequestFactory generationHttp = new SimpleClientHttpRequestFactory();
        generationHttp.setConnectTimeout(connectTimeout);
        // Slow/hung inference must eventually release the request for the next provider.
        generationHttp.setReadTimeout(readTimeout);
        OllamaApi api = OllamaApi.builder().baseUrl(url)
                .restClientBuilder(RestClient.builder().requestFactory(generationHttp)
                        .requestInterceptor(new OllamaThinkingInterceptor(think))).build();
        SimpleClientHttpRequestFactory probeHttp = new SimpleClientHttpRequestFactory();
        probeHttp.setConnectTimeout(connectTimeout);
        probeHttp.setReadTimeout(probeTimeout);
        OllamaApi probe = OllamaApi.builder().baseUrl(url)
                .restClientBuilder(RestClient.builder().requestFactory(probeHttp)).build();
        OllamaOptions options = OllamaOptions.fromOptions(defaults);
        options.setModel(model);
        options.setKeepAlive(keepAlive);
        return new GenerationRouter.Target(new GenerationRouter.Provider(name, url, model),
                OllamaChatModel.builder().ollamaApi(api).defaultOptions(options).build(),
                () -> probe.listModels().models().stream().anyMatch(m -> canonical(model).equals(canonical(m.name()))));
    }

    private static String canonical(String name) { return name.contains(":") ? name : name + ":latest"; }

    @Bean
    RagAuditService ragAuditService(@Value("${r4r.rag.audit.enabled:false}") boolean enabled,
            @Value("${r4r.rag.audit.provider:}") String selection,
            @Value("${r4r.rag.openai.read-timeout:35s}") Duration remoteTimeout,
            @Qualifier("generationTargets") List<GenerationRouter.Target> targets,
            GenerationProviderProperties local, OllamaChatProperties properties,
            org.springframework.beans.factory.ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapper) {
        var json = mapper.getIfAvailable(() -> new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        if (!enabled) return new RagAuditService(false, null, null, null, remoteTimeout, json);
        String[] parts = selection.split("\\|", -1);
        if (parts.length < 2 || parts.length > 3 || parts[1].isBlank() || parts[1].length() > 128)
            throw new IllegalArgumentException("Audit provider requires URL|model[|timeout] or openai|model");
        String model = parts[1].trim();
        if ("openai".equalsIgnoreCase(parts[0].trim())) {
            if (parts.length != 2) throw new IllegalArgumentException("OpenAI audit inherits the existing remote timeout");
            if (remoteTimeout.isNegative() || remoteTimeout.isZero() || remoteTimeout.compareTo(Duration.ofSeconds(90)) > 0)
                throw new IllegalArgumentException("OpenAI audit requires an existing remote timeout within 90s");
            var remote = targets.stream().filter(t -> t.model() instanceof OpenAiResponsesModel).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("OpenAI audit requires explicitly enabled OpenAI generation credentials/quota"));
            return new RagAuditService(true, ((OpenAiResponsesModel) remote.model()).forModel(model),
                    "OpenAI", model, remoteTimeout, json);
        }
        var uri = java.net.URI.create(parts[0].trim());
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
            throw new IllegalArgumentException("Audit requires an explicit Ollama HTTP origin without credentials");
        String url = uri.toString().replaceAll("/$", "");
        var matching = local.providers().stream().filter(p -> p.baseUrl().replaceAll("/$", "").equals(url)).findFirst();
        Duration readTimeout = parts.length == 3 ? org.springframework.boot.convert.DurationStyle.detectAndParse(parts[2].trim())
                : matching.orElse(local.providers().getFirst()).readTimeout();
        if (readTimeout.toMillis() < 2 || readTimeout.compareTo(Duration.ofSeconds(90)) > 0)
            throw new IllegalArgumentException("Audit timeout must be between 2ms and 90s");
        boolean sharedModel = local.providers().stream().anyMatch(p -> p.baseUrl().replaceAll("/$", "").equals(url)
                && canonical(p.model()).equals(canonical(model)));
        var selected = target("Ollama", url, model, properties.getOptions(), local.connectTimeout(), local.probeTimeout(),
                readTimeout, sharedModel ? "3h" : "0s");
        return new RagAuditService(true, selected.model(), "Ollama", model, readTimeout, json);
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
