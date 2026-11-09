package com.riansares.r4r.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.rag.GenerationRouter;
import com.riansares.r4r.rag.RagConfiguration;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
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
            runner().withPropertyValues(embeddingEndpoints(embedding,embedding,embedding)).withPropertyValues(
                    "RAG_GENERATION_PROVIDERS="+url(pc)+"|qwen3:8b|20s,"+url(backup)+"|qwen2.5:7b|30s",
                    // A legacy global URL must never capture either operation.
                    "spring.ai.ollama.base-url=http://127.0.0.1:1")
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(EmbeddingModel.class).hasSingleBean(ChatModel.class);
                        assertThat((List<?>) context.getBean("generationTargets")).hasSize(2);
                        var model = context.getBean(EmbeddingModel.class);
                        assertThat(model.embed("Documento para ingesta")).hasSize(768);
                        assertThat(model.embed("Consulta para recuperar documentos")).hasSize(768);
                        var router = context.getBean(GenerationRouter.class);
                        context.getBean(com.riansares.r4r.rag.LocalRetrievalQueryTranslator.class).translate("What renovations?");
                        assertThat(router.lastUsed()).isNull(); // Translation is not a final answer in runtime.
                        assertThat(router.call(new Prompt("Consulta")).getResult().getOutput().getText()).isEqualTo("pc answer");
                        assertThat(router.lastUsed().baseUrl()).isEqualTo(url(pc));
                        pc.stop(0);
                        assertThat(router.call(new Prompt("Consulta de respaldo")).getResult().getOutput().getText()).isEqualTo("backup answer");
                        assertThat(router.lastUsed().baseUrl()).isEqualTo(url(backup));
                        assertThat(operations).filteredOn(s -> s.contains("/api/embed")).hasSize(2)
                                .allMatch(s -> s.startsWith("embedding ") && s.contains("nomic-embed-text:latest"));
                        assertThat(operations).noneMatch(s -> s.startsWith("embedding ") && s.contains("/api/chat"));
                        assertThat(operations).anyMatch(s -> s.startsWith("pc /api/chat") && s.contains("qwen3:8b") && s.contains("\"temperature\":0.0"));
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

    @Test void enabledRemoteAddsOnlyThirdGenerationTarget() {
        runner().withPropertyValues("r4r.rag.openai.enabled=true", "OPENAI_API_KEY=TEST_ONLY", "r4r.rag.openai.model=gpt-6-luna")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var targets=(List<GenerationRouter.Target>)context.getBean("generationTargets");
                    assertThat(targets).hasSize(3);
                    assertThat(targets.get(2).provider().model()).isEqualTo("gpt-6-luna");
                    assertThat(targets.get(2).provider().name()).isEqualTo("OpenAI");
                });
    }

    @Test void orderedDefaultsIgnoreRemovedAliases() {
        runner().withPropertyValues("RAG_EMBEDDING_BASE_URL=http://legacy:11434",
                "RAG_EMBEDDING_LP_BASE_URL=http://legacy-lp:11434", "RAG_EMBEDDING_PC_BASE_URL=http://legacy-pc:11434",
                "RAG_EMBEDDING_NAS_BASE_URL=http://legacy-nas:11434", "RAG_EMBEDDING_READ_TIMEOUT=99s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var p=context.getBean(EmbeddingFailoverProperties.class);
                    assertThat(p.providers()).extracting(EmbeddingFailoverProperties.Provider::name)
                            .allMatch(name -> name.startsWith("ollama-"));
                    assertThat(p.providers()).extracting(EmbeddingFailoverProperties.Provider::baseUrl)
                            .containsExactly("http://192.168.1.209:11434", "http://192.168.1.207:11434", "http://192.168.1.203:11434");
                    assertThat(p.providers()).extracting(EmbeddingFailoverProperties.Provider::readTimeout)
                            .containsExactly(java.time.Duration.ofSeconds(8), java.time.Duration.ofSeconds(8), java.time.Duration.ofSeconds(30));
                    assertThat(p.dimensions()).isEqualTo(Integer.parseInt(context.getEnvironment()
                            .getProperty("spring.ai.vectorstore.pgvector.dimensions")));
                });
    }
    @Test void incompatibleDimensionsFailBeforeNetwork() {
        runner().withPropertyValues("RAG_EMBEDDING_DIMENSIONS=384")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void ingestionAndQueryShareAllThreeEmbeddingTargets() throws Exception {
        List<String> operations=new CopyOnWriteArrayList<>();
        HttpServer lp=server("lp", "nomic-embed-text:latest",operations);
        HttpServer pc=server("embed-pc", "nomic-embed-text:latest",operations);
        HttpServer nas=server("nas", "nomic-embed-text:latest",operations);
        try {
            runner().withPropertyValues(embeddingEndpoints(lp,pc,nas))
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(EmbeddingModel.class);
                        var model=context.getBean(EmbeddingModel.class);
                        assertThat(model.embed(new org.springframework.ai.document.Document("Ingestion document"))).hasSize(768);
                        lp.stop(0); assertThat(model.embed("Retrieval query")).hasSize(768);
                        pc.stop(0); assertThat(model.embed("Next query")).hasSize(768);
                        assertThat(operations).filteredOn(v -> v.contains("/api/embed"))
                                .extracting(v -> v.split(" ")[0]).containsExactly("lp", "embed-pc", "nas");
                    });
        } finally { lp.stop(0); pc.stop(0); nas.stop(0); }
    }

    @Test void fourLocalTargetsFailOverInOrderAndLunaStaysLast() throws Exception {
        List<String> operations=new CopyOnWriteArrayList<>();
        HttpServer a=server("a","model-a",operations), b=server("b","model-b",operations),
                c=server("c","model-c",operations), d=server("d","model-d",operations);
        try {
            runner().withBean(ObjectMapper.class, ObjectMapper::new).withUserConfiguration(com.riansares.r4r.rag.OllamaModelPreloader.class)
                    .withPropertyValues("r4r.rag.warmup.on-startup=false", "r4r.rag.openai.enabled=true", "OPENAI_API_KEY=TEST_ONLY",
                            "RAG_GENERATION_PROVIDERS="+url(a)+"|model-a|1s,"+url(b)+"|model-b|2s,"+url(c)+"|model-c|3s,"+url(d)+"|model-d|4s")
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(com.riansares.r4r.rag.OllamaModelPreloader.class);
                        var configured=context.getBean(GenerationProviderProperties.class).providers();
                        assertThat(configured).extracting(GenerationProviderProperties.Provider::readTimeout)
                                .containsExactly(java.time.Duration.ofSeconds(1),java.time.Duration.ofSeconds(2),
                                        java.time.Duration.ofSeconds(3),java.time.Duration.ofSeconds(4));
                        var targets=(List<GenerationRouter.Target>)context.getBean("generationTargets");
                        assertThat(targets).hasSize(5);
                        assertThat(targets.getLast().provider().name()).isEqualTo("OpenAI");
                        // The preloader constructor has not contacted the NAS or any other endpoint.
                        assertThat(operations).isEmpty();
                        a.stop(0); b.stop(0); c.stop(0);
                        var router=context.getBean(GenerationRouter.class);
                        assertThat(router.call(new Prompt("Consulta")).getResult().getOutput().getText()).isEqualTo("d answer");
                        assertThat(router.lastUsed().baseUrl()).isEqualTo(url(d));
                        context.getBean(com.riansares.r4r.rag.LocalRetrievalQueryTranslator.class).translate("Test question");
                        assertThat(operations).filteredOn(op -> op.contains("/api/chat")).hasSize(2)
                                .allMatch(op -> op.startsWith("d "));
                    });
        } finally { a.stop(0); b.stop(0); c.stop(0); d.stop(0); }
    }

    private String[] embeddingEndpoints(HttpServer lp, HttpServer pc, HttpServer nas) {
        return new String[] {
                "r4r.rag.embedding.providers[0].name=laptop", "r4r.rag.embedding.providers[0].base-url="+url(lp),
                "r4r.rag.embedding.providers[0].read-timeout=8s",
                "r4r.rag.embedding.providers[1].name=desktop", "r4r.rag.embedding.providers[1].base-url="+url(pc),
                "r4r.rag.embedding.providers[1].read-timeout=8s",
                "r4r.rag.embedding.providers[2].name=nas", "r4r.rag.embedding.providers[2].base-url="+url(nas),
                "r4r.rag.embedding.providers[2].read-timeout=30s" };
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getBeanFactory().setConversionService(org.springframework.boot.convert.ApplicationConversionService.getSharedInstance());
                    try {
                        var publicDefaults = new java.util.Properties();
                        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of("docker-spring/.env.base"))) {
                            publicDefaults.load(input);
                        }
                        context.getEnvironment().getPropertySources().addLast(
                                new org.springframework.core.env.PropertiesPropertySource("public-base", publicDefaults));
                        PublicRagEnvironment.apply(context.getEnvironment());
                        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
                            context.getEnvironment().getPropertySources().addLast(source);
                        }
                    } catch (Exception e) { throw new IllegalStateException(e); }
                })
                .withConfiguration(AutoConfigurations.of(OllamaEmbeddingAutoConfiguration.class, OllamaChatAutoConfiguration.class))
                .withUserConfiguration(OllamaEmbeddingConfiguration.class, RagConfiguration.class)
                .withBean(KnowledgeVectorStore.class, () -> mock(KnowledgeVectorStore.class));
    }

    private HttpServer server(String name, String model, List<String> operations) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            operations.add(name + " " + exchange.getRequestURI().getPath() + " " + body);
            Object payload = switch (exchange.getRequestURI().getPath()) {
                case "/api/embed" -> Map.of("model", model, "embeddings", List.of(vector()));
                case "/api/tags" -> Map.of("models", List.of(Map.of("name", model, "digest", "0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f")));
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

    private static float[] vector() { float[] v = new float[768]; v[0] = 1; return v; }

    private static String url(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }
}
