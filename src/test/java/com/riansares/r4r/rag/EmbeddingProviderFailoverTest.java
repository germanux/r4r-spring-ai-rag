package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.config.EmbeddingFailoverProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.client.RestClientResponseException;
import static org.assertj.core.api.Assertions.*;

/** Actual Spring AI HTTP clients, simulated Ollama servers; no physical services. */
class EmbeddingProviderFailoverTest {
    static final String MODEL = "nomic-embed-text:latest";
    static final String DIGEST = "0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f";
    final List<Node> nodes = new ArrayList<>();
    final List<String> calls = new CopyOnWriteArrayList<>();
    final OllamaOptions options = OllamaOptions.builder().model(MODEL).build();
    @AfterEach void close() { nodes.forEach(n -> n.server.stop(0)); Thread.interrupted(); }

    @Test void laptopFirstAndOnly() throws Exception {
        var r = router(node("lp"), node("pc"), node("nas"));
        assertThat(r.call(request()).getResult().getOutput()).hasSize(768);
        assertThat(calls).containsExactly("lp /api/tags", "lp /api/embed");
    }
    @Test void desktopFallbackAndLaptopRecoveryNextRequest() throws Exception {
        Node lp = node("lp"), pc = node("pc"), nas = node("nas"); lp.status = 503;
        var r = router(lp, pc, nas); r.call(request());
        assertThat(calls).containsExactly("lp /api/tags", "pc /api/tags", "pc /api/embed");
        calls.clear(); lp.status = 200; r.call(request());
        assertThat(calls).containsExactly("lp /api/tags", "lp /api/embed");
    }
    @Test void nasAfterBothUnavailable() throws Exception {
        Node lp = node("lp"), pc = node("pc"), nas = node("nas"); lp.status=503; pc.status=502;
        router(lp,pc,nas).call(request());
        assertThat(calls).containsExactly("lp /api/tags", "pc /api/tags", "nas /api/tags", "nas /api/embed");
    }
    @Test void allDownExplicitUnavailable() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas"); a.status=b.status=c.status=503;
        assertThatThrownBy(() -> router(a,b,c).call(request())).isInstanceOf(EmbeddingUnavailableException.class);
        assertThat(calls).hasSize(3);
    }
    @Test void connectionRefusedFallsBack() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas"); a.server.stop(0);
        router(a,b,c).call(request()); assertThat(calls).containsExactly("pc /api/tags", "pc /api/embed");
    }
    @Test void digestMismatchStopsWithoutFallback() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas"); a.digest="f".repeat(64);
        assertThatThrownBy(() -> router(a,b,c).call(request())).isInstanceOf(EmbeddingConfigurationException.class);
        assertThat(calls).containsExactly("lp /api/tags");
    }
    @Test void missingModelStopsWithoutFallback() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas"); a.model="other:latest";
        assertThatThrownBy(() -> router(a,b,c).call(request())).isInstanceOf(EmbeddingConfigurationException.class);
        assertThat(calls).hasSize(1);
    }
    @Test void invalidParametersAndAuthNeverFallback() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas"); var r=router(a,b,c);
        for (int status : List.of(400,401,403,404,422)) {
            calls.clear(); a.status=status;
            assertThatThrownBy(() -> r.call(request())).isInstanceOf(RestClientResponseException.class);
            assertThat(calls).containsExactly("lp /api/tags");
        }
    }
    @Test void transientHttpStatusesFallback() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas"); var r=router(a,b,c);
        for (int status : List.of(408,429,500,502,503,504)) {
            calls.clear(); a.status=status; r.call(request());
            assertThat(calls).containsExactly("lp /api/tags", "pc /api/tags", "pc /api/embed");
        }
    }
    @Test void invalidVectorsNeverAppearSuccessfulOrTriggerFallback() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas"); var r=router(a,b,c);
        for (int dimensions : List.of(0,767,769)) {
            calls.clear(); a.dimensions=dimensions;
            assertThatThrownBy(() -> r.call(request())).isInstanceOf(EmbeddingConfigurationException.class);
            assertThat(calls).hasSize(2);
        }
        a.dimensions=768; a.zero=true;
        assertThatThrownBy(() -> r.call(request())).isInstanceOf(EmbeddingConfigurationException.class);
    }
    @Test void wrongResponseModelRejected() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas"); a.responseModel="other:latest";
        assertThatThrownBy(() -> router(a,b,c).call(request())).isInstanceOf(EmbeddingConfigurationException.class);
    }
    @Test void badInputsAndRuntimeOverridesDoNotCallServers() throws Exception {
        var r=router(node("lp"),node("pc"),node("nas"));
        assertThatThrownBy(() -> r.call(new EmbeddingRequest(List.of(""),null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.call(new EmbeddingRequest(List.of("text"), OllamaOptions.builder().model("other").build())))
                .isInstanceOf(EmbeddingConfigurationException.class);
        assertThat(calls).isEmpty();
    }
    @Test void cancelledRequestNeverCallsProviders() throws Exception {
        var r=router(node("lp"),node("pc"),node("nas")); var progress=RagProgress.silent(); progress.cancel();
        assertThatThrownBy(() -> progress.duringRetrieval(() -> r.call(request()))).isInstanceOf(CancellationException.class);
        assertThat(calls).isEmpty();
    }
    @Test void interruptionNeverFallsBack() throws Exception {
        var r=router(node("lp"),node("pc"),node("nas")); Thread.currentThread().interrupt();
        assertThatThrownBy(() -> r.call(request())).isInstanceOf(CancellationException.class); assertThat(calls).isEmpty();
    }
    @Test void oneMeasuredEmbeddingPairIncludesFailedAttemptAndNoProbeEmbedding() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas"); a.status=503;
        var observed=new ObservedEmbeddingModel(OllamaApi.builder().baseUrl(a.url()).build(),options,router(a,b,c));
        List<Map<String,Object>> events=new ArrayList<>(); var progress=new RagProgress(events::add);
        assertThat(observed.dimensions()).isEqualTo(768); assertThat(calls).isEmpty();
        progress.duringRetrieval(() -> observed.embed("private input"));
        assertThat(events).extracting(e -> e.get("stage")).containsExactly("EMBEDDING_STARTED","EMBEDDING_COMPLETED");
        assertThat((Double)progress.metrics().get("embeddingMs")).isGreaterThan(0);
        assertThat(calls).filteredOn(s -> s.endsWith("/api/embed")).hasSize(1);
    }
    @Test void timeoutIsRecoverable() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas"); a.delay=200;
        router(a,b,c).call(request()); assertThat(calls).contains("pc /api/embed");
    }
    @Test void incompatibleConfiguredDimensionsRejected() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas");
        var p=properties(a,b,c,767);
        assertThatThrownBy(() -> new EmbeddingProviderRouter(p,options,Duration.ofSeconds(3)))
                .isInstanceOf(EmbeddingConfigurationException.class);
        assertThat(calls).isEmpty();
    }
    @Test void elapsedSseDeadlinePreventsNetworkAndScopeIsReleased() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas");
        var p=new EmbeddingFailoverProperties(a.url(),b.url(),c.url(),Duration.ofMillis(5),Duration.ofMillis(10),
                Duration.ofMillis(10),Duration.ofMillis(10),Duration.ofMillis(10),Duration.ofMillis(20),768,DIGEST);
        var r=new EmbeddingProviderRouter(p,options,Duration.ofMillis(20));
        var progress=RagProgress.silent(); Thread.sleep(30);
        assertThatThrownBy(() -> progress.duringRetrieval(() -> r.call(request())))
                .isInstanceOf(EmbeddingUnavailableException.class);
        assertThat(calls).isEmpty();
        // A later ingestion call has its own budget; the completed request scope cannot leak.
        router(a,b,c).call(request()); assertThat(calls).contains("lp /api/embed");
    }
    @Test void logsIdentifyAttemptsTimingsAndFinalProviderWithoutInput() throws Exception {
        Node a=node("lp"), b=node("pc"), c=node("nas"); a.status=503;
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(EmbeddingProviderRouter.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            router(a,b,c).call(request());
            var messages=appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).anyMatch(m -> m.contains("from=laptop to=desktop"));
            var measured=messages.stream().filter(m -> m.contains("attemptMs=")).toList();
            assertThat(measured).hasSize(2);
            String id=measured.getFirst().split("id=")[1].split(" ")[0];
            for (String line:measured) {
                assertThat(line).contains("id="+id, "model="+MODEL);
                double attempt=Double.parseDouble(line.split("attemptMs=")[1].split(" ")[0]);
                double total=Double.parseDouble(line.split("totalMs=")[1]);
                assertThat(attempt).isGreaterThanOrEqualTo(0); assertThat(total).isGreaterThanOrEqualTo(attempt);
            }
            assertThat(measured.getLast()).contains("provider=desktop", "outcome=success");
            assertThat(messages).noneMatch(m -> m.contains("private input") || m.contains("127.0.0.1"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
    EmbeddingProviderRouter router(Node a, Node b, Node c) {
        return new EmbeddingProviderRouter(properties(a,b,c,768),options,Duration.ofSeconds(3));
    }
    EmbeddingFailoverProperties properties(Node a, Node b, Node c, int dimensions) {
        return new EmbeddingFailoverProperties(a.url(),b.url(),c.url(),Duration.ofMillis(50),Duration.ofMillis(100),
                Duration.ofSeconds(1),Duration.ofSeconds(1),Duration.ofMillis(100),Duration.ofSeconds(2),dimensions,DIGEST);
    }
    EmbeddingRequest request() { return new EmbeddingRequest(List.of("private input"),null); }
    Node node(String name) throws Exception { var n=new Node(name); nodes.add(n); return n; }
    class Node {
        final HttpServer server;
        volatile int status=200, dimensions=768, delay=0;
        volatile String model=MODEL, responseModel=MODEL, digest=DIGEST;
        volatile boolean zero;
        Node(String name) throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/", exchange -> {
                calls.add(name+" "+exchange.getRequestURI().getPath()); exchange.getRequestBody().readAllBytes();
                try {
                    if (delay>0) Thread.sleep(delay);
                    float[] vector=new float[dimensions]; if (dimensions>0 && !zero) vector[0]=1;
                    Object payload=exchange.getRequestURI().getPath().equals("/api/tags")
                            ? Map.of("models",List.of(Map.of("name",model,"digest",digest)))
                            : Map.of("model",responseModel,"embeddings",List.of(vector));
                    byte[] bytes=new ObjectMapper().writeValueAsBytes(payload);
                    exchange.getResponseHeaders().set("Content-Type","application/json");
                    exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes);
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            }); server.start();
        }
        String url() { return "http://127.0.0.1:"+server.getAddress().getPort(); }
    }
}
