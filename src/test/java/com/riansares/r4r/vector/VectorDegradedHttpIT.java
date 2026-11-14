package com.riansares.r4r.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.rag.*;
import com.riansares.r4r.rag.api.RagStreamController;
import java.net.URI;
import java.net.http.*;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MVC/Flyway startup and HTTP with unavailable simulated PostgreSQL, simulated AWS and LLM. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.model.embedding=ollama",
        "spring.flyway.enabled=true", "spring.sql.init.mode=never", "r4r.catalog-on-startup=false",
        "r4r.rag.vector.primary=pgvector", "r4r.rag.vector.failover-enabled=true", "r4r.rag.vector.s3vectors.enabled=true",
        "r4r.rag.openai.enabled=false", "r4r.rag.warmup.on-startup=false", "r4r.api-security.enabled=true",
        "r4r.api-security.min-interval=0s", "r4r.api-security.max-global-requests-per-minute=1000" })
@Import({VectorDegradedHttpIT.OfflineDatabase.class, PgVectorTestConfiguration.class})
@Tag("offline")
class VectorDegradedHttpIT {
    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineDatabase {
        @Bean DataSource dataSource() throws Exception {
            var source = mock(DataSource.class);
            when(source.getConnection()).thenThrow(new SQLException("simulated unavailable database", "08001", new java.net.UnknownHostException("postgres")));
            return source;
        }
    }
    @LocalServerPort int port;
    @MockBean S3VectorsClient aws;
    @MockBean GenerationRouter router;
    @MockBean OllamaModelPreloader preloader;
    @Autowired DataSource database;
    @Autowired RagStreamController streams;
    @Autowired org.springframework.ai.ollama.OllamaEmbeddingModel embedding;
    final ObjectMapper json = new ObjectMapper();
    final GenerationRouter.Provider provider = new GenerationRouter.Provider("PC principal", "http://simulated-pc", "qwen3:8b");
    @BeforeEach void responses() {
        // A separate relevance call precedes the existing answer/SSE routing contract.
        when(router.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new AssistantMessage("{\"sources\":[1],\"coverage\":\"full\"}")))));
        var metadata = Document.fromMap(Map.of("source", Document.fromString("faq.md"),
                "source_text", Document.fromString("La empresa reforma viviendas y oficinas."),
                "headingPath", Document.fromList(List.of(Document.fromString("Servicios"))), "ordinal", Document.fromNumber(2)));
        when(aws.queryVectors(any(QueryVectorsRequest.class))).thenReturn(QueryVectorsResponse.builder().distanceMetric(DistanceMetric.COSINE)
                .vectors(QueryOutputVector.builder().key("existing-document").distance(.1f).metadata(metadata).build()).build());
        when(router.preferred()).thenReturn(provider);
        when(router.execution()).thenReturn(new GenerationRouter.Execution(null, false));
        when(router.requestExecution()).thenReturn(new GenerationRouter.Execution(provider, false));
        when(router.call(any(Prompt.class), any(RagProgress.class))).thenAnswer(invocation -> {
            RagProgress progress = invocation.getArgument(1); progress.usedProvider(provider);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Reforma viviendas y oficinas. [S1]"))));
        });
        when(preloader.modelStates()).thenReturn(List.of());
        when(preloader.memoryFor(any())).thenReturn(new OllamaModelPreloader.MemoryState(false, List.of()));
    }
    HttpResponse<String> post(String endpoint) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + endpoint))
                .timeout(java.time.Duration.ofSeconds(5)).header("Origin", "http://127.0.0.1:8000")
                .header("Content-Type", "application/json").header("Accept", endpoint.endsWith("stream") ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"Consulta\",\"responseLanguage\":\"es\"}")).build();
        try (var client = HttpClient.newHttpClient()) { return client.send(request, HttpResponse.BodyHandlers.ofString()); }
    }
    @Test void coldStartupServesCitedAwsAnswerWithConfiguredModelsAndOriginalContract() throws Exception {
        var response = post("/api/rag/answers");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        assertThat(body.path("abstained").asBoolean()).isFalse();
        assertThat(body.path("answer").asText()).contains("[S1]");
        assertThat(body.at("/citations/0/source").asText()).isEqualTo("faq.md");
        assertThat(body.at("/citations/0/ordinal").asInt()).isEqualTo(2);
        assertThat(body.at("/citations/0/headingPath/0").asText()).isEqualTo("Servicios");
        assertThat(body.at("/provider/model").asText()).isEqualTo("qwen3:8b");
        verify(aws).queryVectors(argThat((QueryVectorsRequest request) -> request.queryVector().float32().size() == 1024
                && request.returnMetadata() && request.indexName().equals("amzn-r4r-vector-index")));
        assertThat(embedding.dimensions()).isEqualTo(1024);
    }
    @Test void coldStartupAlsoServesSseAndPreservesCitations() throws Exception {
        var response = post("/api/rag/answers/stream");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("event:ANSWER_READY", "faq.md", "\"ordinal\":2", "qwen3:8b")
                .doesNotContain("event:ERROR", "simulated unavailable", "simulated-pc");
    }
    @Test void bothStoresFailWithoutInventingAnAnswerAndRuntimeStillWorks() throws Exception {
        when(aws.queryVectors(any(QueryVectorsRequest.class))).thenThrow(SdkClientException.create("synthetic private response"));
        var rest = post("/api/rag/answers");
        assertThat(rest.statusCode()).isEqualTo(503);
        assertThat(rest.body()).doesNotContain("synthetic private", "existing-document", "faq.md");
        var sse = post("/api/rag/answers/stream");
        assertThat(sse.statusCode()).isEqualTo(200);
        assertThat(sse.body()).contains("event:ERROR").doesNotContain("event:ANSWER_READY", "synthetic private");
        try (var client = HttpClient.newHttpClient()) {
            var runtime = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/rag/runtime")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(runtime.statusCode()).isEqualTo(200);
            assertThat(json.readTree(runtime.body()).path("embeddingModel").asText()).isEqualTo("qwen3-embedding:0.6b");
        }
        verify(router, never()).call(any(Prompt.class), any(RagProgress.class));
    }
}
