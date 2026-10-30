package com.riansares.r4r.rag;

import com.riansares.r4r.config.RagRetrievalProperties;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RagRetrievalProperties.class)
public class RagConfiguration {

    @Bean
    GenerationRouter generationRouter(OllamaChatProperties properties,
            @Value("${spring.ai.ollama.base-url}") String fallbackUrl,
            @Value("${r4r.rag.generation.pc-base-url}") String pcUrl,
            @Value("${r4r.rag.generation.pc-model}") String pcModel,
            @Value("${r4r.rag.generation.connect-timeout:2s}") Duration connectTimeout,
            @Value("${r4r.rag.generation.probe-timeout:2s}") Duration probeTimeout) {
        return new GenerationRouter(target("PC principal", pcUrl, pcModel, properties.getOptions(), connectTimeout, probeTimeout),
                target("Portátil ASUS", fallbackUrl, properties.getOptions().getModel(), properties.getOptions(), connectTimeout, probeTimeout));
    }

    private GenerationRouter.Target target(String name, String url, String model, OllamaOptions defaults,
                                           Duration connectTimeout, Duration probeTimeout) {
        SimpleClientHttpRequestFactory generationHttp = new SimpleClientHttpRequestFactory();
        generationHttp.setConnectTimeout(connectTimeout);
        // No read timeout: slow inference must not trigger failover.
        OllamaApi api = OllamaApi.builder().baseUrl(url)
                .restClientBuilder(RestClient.builder().requestFactory(generationHttp)).build();
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
            PgVectorKnowledgeStore knowledgeStore,
            GenerationRouter chatModel,
            RagRetrievalProperties properties) {

        return new CitedRagService(
                knowledgeStore,
                chatModel,
                properties.topK(),
                properties.minScore());
    }
}
