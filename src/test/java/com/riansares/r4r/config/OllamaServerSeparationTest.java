package com.riansares.r4r.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.rag.GenerationRouter;
import com.riansares.r4r.rag.RagConfiguration;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Real Spring AI HTTP clients against simulated Ollama servers; no LLM or database needed. */
class OllamaServerSeparationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void isolatesEmbeddingIngestionAndQueriesFromPreferredGenerationAndFallback() throws Exception {
        List<String> operations = new CopyOnWriteArrayList<>();
        HttpServer embedding = server("embedding", "nomic-embed-text:latest", operations);
        HttpServer pc = server("pc", "qwen3:8b", operations);
        HttpServer backup = server("backup", "qwen2.5:7b", operations);
        try {
            runner().withPropertyValues(
                    "r4r.rag.embedding.base-url=" + url(embedding),
                    "r4r.rag.generation.pc-base-url=" + url(pc),
                    "r4r.rag.generation.fallback-base-url=" + url(backup),
                    // A legacy global URL must never capture either operation.
                    "spring.ai.ollama.base-url=http://127.0.0.1:1")
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(EmbeddingModel.class).hasSingleBean(ChatModel.class);
                        var model = context.getBean(EmbeddingModel.class);
                        assertThat(model.embed("Documento para ingesta")).hasSize(768);
                        assertThat(model.embed("Consulta para recuperar documentos")).hasSize(768);
                        var router = context.getBean(GenerationRouter.class);
                        assertThat(router.call(new Prompt("Consulta")).getResult().getOutput().getText()).isEqualTo("pc answer");
                        assertThat(router.lastUsed().baseUrl()).isEqualTo(url(pc));
                        pc.stop(0);
                        assertThat(router.call(new Prompt("Consulta de respaldo")).getResult().getOutput().getText()).isEqualTo("backup answer");
                        assertThat(router.lastUsed().baseUrl()).isEqualTo(url(backup));
                        assertThat(operations).filteredOn(s -> s.contains("/api/embed")).hasSize(2)
                                .allMatch(s -> s.startsWith("embedding ") && s.contains("nomic-embed-text:latest"));
                        assertThat(operations).noneMatch(s -> s.startsWith("embedding ") && s.contains("/api/chat"));
                        assertThat(operations).anyMatch(s -> s.startsWith("pc /api/chat") && s.contains("qwen3:8b"));
                        assertThat(operations).anyMatch(s -> s.startsWith("backup /api/chat") && s.contains("qwen2.5:7b"));
                        assertThat(context.getEnvironment().getProperty("spring.ai.vectorstore.pgvector.dimensions")).isEqualTo("768");
                    });
        } finally { embedding.stop(0); pc.stop(0); backup.stop(0); }
    }

    @Test
    void disabledEmbeddingRetainsDeterministicTestConfiguration() {
        runner().withPropertyValues("spring.ai.model.embedding=none")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EmbeddingModel.class));
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getBeanFactory().setConversionService(org.springframework.boot.convert.ApplicationConversionService.getSharedInstance());
                    try {
                        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
                            context.getEnvironment().getPropertySources().addLast(source);
                        }
                    } catch (Exception e) { throw new IllegalStateException(e); }
                })
                .withConfiguration(AutoConfigurations.of(OllamaEmbeddingAutoConfiguration.class, OllamaChatAutoConfiguration.class))
                .withUserConfiguration(OllamaEmbeddingConfiguration.class, RagConfiguration.class)
                .withBean(PgVectorKnowledgeStore.class, () -> mock(PgVectorKnowledgeStore.class));
    }

    private HttpServer server(String name, String model, List<String> operations) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            operations.add(name + " " + exchange.getRequestURI().getPath() + " " + body);
            Object payload = switch (exchange.getRequestURI().getPath()) {
                case "/api/embed" -> Map.of("model", model, "embeddings", List.of(new float[768]));
                case "/api/tags" -> Map.of("models", List.of(Map.of("name", model)));
                case "/api/chat" -> Map.of("model", model, "message", Map.of("role", "assistant", "content", name + " answer"), "done", true);
                default -> Map.of();
            };
            byte[] bytes = mapper.writeValueAsBytes(payload);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String url(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }
}
