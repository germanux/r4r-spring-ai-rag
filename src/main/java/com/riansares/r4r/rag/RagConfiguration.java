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

    @Bean
    LocalRetrievalQueryTranslator retrievalQueryTranslator(
            @Qualifier("generationTargets") List<GenerationRouter.Target> targets,
            OllamaChatProperties properties,
            GenerationProviderProperties local) {
        var translation = new java.util.ArrayList<GenerationRouter.Target>();
        for (var provider:local.providers()) translation.add(target(provider.name(),provider.baseUrl(),provider.model(),
                properties.getOptions(),local.connectTimeout(),local.probeTimeout(),
                local.translationReadTimeout().compareTo(provider.readTimeout())<0
                        ? local.translationReadTimeout() : provider.readTimeout()));
        // Share the remote quota, independently of the number of local providers.
        targets.stream().filter(t -> "OpenAI".equals(t.provider().name())).forEach(translation::add);
        return new LocalRetrievalQueryTranslator(new GenerationRouter(translation));
    }

    private GenerationRouter.Target target(String name, String url, String model, OllamaOptions defaults,
                                           Duration connectTimeout, Duration probeTimeout, Duration readTimeout) {
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
        options.setKeepAlive("3h");
        return new GenerationRouter.Target(new GenerationRouter.Provider(name, url, model),
                OllamaChatModel.builder().ollamaApi(api).defaultOptions(options).build(),
                () -> probe.listModels().models().stream().anyMatch(m -> canonical(model).equals(canonical(m.name()))));
    }

    private static String canonical(String name) { return name.contains(":") ? name : name + ":latest"; }

    @Bean
    CitedRagService citedRagService(
            KnowledgeVectorStore knowledgeStore,
            GenerationRouter chatModel,
            RagRetrievalProperties properties,
            LocalRetrievalQueryTranslator translator,
            @Value("${r4r.rag.evidence-relevance.enabled:false}") boolean relevanceEnabled,
            @Value("${retrieval.translation.mode:never}") String translationMode,
            @Value("${r4r.rag.evaluation.capture-directory:}") String captureDirectory,
            org.springframework.beans.factory.ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> json) {

        return new CitedRagService(
                knowledgeStore,
                chatModel,
                properties.topK(),
                properties.minScore(), translator, relevanceEnabled ? new EvidenceRelevanceFilter(chatModel) : null,
                com.riansares.r4r.config.RetrievalTranslationMode.parse(translationMode),
                captureDirectory.isBlank() ? null : new RagEvidenceExporter(java.nio.file.Path.of(captureDirectory),
                        json.getIfAvailable(() -> new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules())));
    }
}
