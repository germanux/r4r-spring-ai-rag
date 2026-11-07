package com.riansares.r4r.rag;

import com.riansares.r4r.config.RagRetrievalProperties;
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
@EnableConfigurationProperties({RagRetrievalProperties.class, OllamaChatProperties.class})
public class RagConfiguration {

    @Value("${spring.ai.ollama.chat.think:false}")
    private boolean think;

    @Bean
    List<GenerationRouter.Target> generationTargets(OllamaChatProperties properties,
            @Value("${r4r.rag.generation.fallback-base-url}") String fallbackUrl,
            @Value("${r4r.rag.generation.fallback-model}") String fallbackModel,
            @Value("${r4r.rag.generation.pc-base-url}") String pcUrl,
            @Value("${r4r.rag.generation.pc-model}") String pcModel,
            @Value("${r4r.rag.generation.connect-timeout:2s}") Duration connectTimeout,
            @Value("${r4r.rag.generation.probe-timeout:2s}") Duration probeTimeout,
            @Value("${r4r.rag.generation.pc-read-timeout:35s}") Duration pcReadTimeout,
            @Value("${r4r.rag.generation.fallback-read-timeout:60s}") Duration fallbackReadTimeout,
            @Value("${r4r.rag.openai.enabled:false}") boolean remoteEnabled,
            @Value("${r4r.rag.openai.model:gpt-6-luna}") String remoteModel,
            @Value("${r4r.rag.openai.read-timeout:35s}") Duration remoteReadTimeout,
            @Value("${r4r.rag.openai.max-calls-per-day:20}") int remoteDailyCallLimit,
            @Value("${OPENAI_API_KEY:}") String apiKey) {
        var targets = new java.util.ArrayList<>(List.of(target("PC principal", pcUrl, pcModel, properties.getOptions(), connectTimeout, probeTimeout, pcReadTimeout),
                target("Portátil ASUS", fallbackUrl, fallbackModel, properties.getOptions(), connectTimeout, probeTimeout, fallbackReadTimeout)));
        if (remoteEnabled) {
            if (apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY required when remote generation is enabled");
            SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
            http.setConnectTimeout(Duration.ofSeconds(3));
            http.setReadTimeout(remoteReadTimeout);
            var remote = new OpenAiResponsesModel(RestClient.builder().baseUrl("https://api.openai.com/v1")
                    .requestFactory(http).build(), apiKey, remoteModel, remoteDailyCallLimit);
            targets.add(new GenerationRouter.Target(new GenerationRouter.Provider("OpenAI", "https://api.openai.com/v1", remoteModel), remote, () -> true));
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
            @Value("${r4r.rag.generation.pc-base-url}") String pcUrl,
            @Value("${r4r.rag.generation.pc-model}") String pcModel,
            @Value("${r4r.rag.generation.fallback-base-url}") String fallbackUrl,
            @Value("${r4r.rag.generation.fallback-model}") String fallbackModel,
            @Value("${r4r.rag.generation.connect-timeout:2s}") Duration connectTimeout,
            @Value("${r4r.rag.generation.probe-timeout:2s}") Duration probeTimeout,
            @Value("${r4r.rag.generation.translation-read-timeout:30s}") Duration translationReadTimeout) {
        // Bound each translation attempt within the 180-second stream/browser budget.
        // Share remote call quota with generation.
        var translation = new java.util.ArrayList<>(List.of(
                target("PC principal", pcUrl, pcModel, properties.getOptions(), connectTimeout, probeTimeout, translationReadTimeout),
                target("Portátil ASUS", fallbackUrl, fallbackModel, properties.getOptions(), connectTimeout, probeTimeout, translationReadTimeout)));
        if (targets.size() > 2) translation.add(targets.get(2));
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
            LocalRetrievalQueryTranslator translator) {

        return new CitedRagService(
                knowledgeStore,
                chatModel,
                properties.topK(),
                properties.minScore(), translator);
    }
}
