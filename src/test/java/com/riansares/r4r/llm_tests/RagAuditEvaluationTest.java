package com.riansares.r4r.llm_tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.http.*;
import java.nio.file.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RagAuditEvaluationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = mock(HttpClient.class);
    private final String token = "12345678-1234-1234-1234-123456789abc";
    @TempDir Path directory;

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(body);
        return response;
    }
    private ObjectNode rag(String status) {
        return json.createObjectNode().put("status", status).put("answer", "Original answer [S1].").put("auditId", token);
    }
    private String review(String original, String recommended, String effective, String verdict) throws Exception {
        return json.writeValueAsString(Map.of("originalStatus", original, "recommendedStatus", recommended,
                "effectiveStatus", effective, "conclusive", true, "note", "A limitation remains.",
                "metadata", Map.of("verdict", verdict, "reason", "The source does not establish site feasibility.",
                        "provider", "Ollama", "model", "qwen3:8b", "durationMs", 20)));
    }

    @Test void fetchesAuditAfterRagAndRetainsBothStatesMetadataAndIndependentTimes() throws Exception {
        var rag = rag("ANSWERED");var before = rag.deepCopy();
        var response = response(200, review("ANSWERED", "PARTIAL", "PARTIAL", "FAIL"));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        var audit = RagAuditEvaluation.audit(client, "http://localhost", rag);
        assertThat(audit.path("outcome").asText()).isEqualTo("COMPLETED");
        assertThat(audit.path("proposedDowngrade").asBoolean()).isTrue();
        assertThat(audit.path("appliedDowngrade").asBoolean()).isTrue();
        assertThat(audit.path("effectiveStatus").asText()).isEqualTo("PARTIAL");
        assertThat(audit.path("model").asText()).isEqualTo("qwen3:8b");
        assertThat(audit.path("durationMs").asDouble()).isEqualTo(20);
        assertThat(audit.path("httpLatencyMs").asDouble()).isGreaterThanOrEqualTo(0);
        assertThat(rag).isEqualTo(before);
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().method()).isEqualTo("POST");
        assertThat(request.getValue().uri().getPath()).isEqualTo("/api/rag/audits/" + token);
        assertThat(request.getValue().bodyPublisher().orElseThrow().contentLength()).isZero();
    }

    @Test void acceptsUpgradeRecommendationWithoutApplyingItAndRejectsAnAppliedUpgrade() throws Exception {
        var upgrade = response(200, review("PARTIAL", "ANSWERED", "PARTIAL", "PASS"));
        var unsafe = response(200, review("PARTIAL", "ANSWERED", "ANSWERED", "PASS"));
        var isolatedFail = response(200, review("ANSWERED", "ANSWERED", "ANSWERED", "FAIL"));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(upgrade, unsafe, isolatedFail);
        var valid = RagAuditEvaluation.audit(client, "http://localhost", rag("PARTIAL"));
        assertThat(valid.path("outcome").asText()).isEqualTo("COMPLETED");
        assertThat(valid.path("recommendedStatus").asText()).isEqualTo("ANSWERED");
        assertThat(valid.path("effectiveStatus").asText()).isEqualTo("PARTIAL");
        var invalid = RagAuditEvaluation.audit(client, "http://localhost", rag("PARTIAL"));
        assertThat(invalid.path("outcome").asText()).isEqualTo("ERROR");
        assertThat(invalid.path("effectiveStatus").asText()).isEqualTo("PARTIAL");
        assertThat(RagAuditEvaluation.audit(client, "http://localhost", rag("ANSWERED")).path("outcome").asText()).isEqualTo("ERROR");
    }

    @Test void absenceErrorsAndOldMetadataNeverDiscardOrFailTheRagResult() throws Exception {
        var noId = json.createObjectNode().put("status", "ABSTAINED");
        assertThat(RagAuditEvaluation.audit(client, "http://localhost", noId).path("outcome").asText()).isEqualTo("SKIPPED");
        verifyNoInteractions(client);
        var expired = response(404, ""); var busy = response(503, ""); var invalid = response(200, "not JSON");
        var oldApi = response(200, "{\"originalStatus\":\"ANSWERED\",\"recommendedStatus\":\"PARTIAL\",\"effectiveStatus\":\"PARTIAL\",\"conclusive\":true}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(expired, busy, invalid, oldApi)
                .thenThrow(new java.net.http.HttpTimeoutException("private token"));
        for (String expected : new String[]{"SKIPPED", "ERROR", "ERROR", "ERROR", "ERROR"}) {
            var audit = RagAuditEvaluation.audit(client, "http://localhost", rag("ANSWERED"));
            assertThat(audit.path("outcome").asText()).isEqualTo(expected);
            assertThat(audit.path("effectiveStatus").asText()).isEqualTo("ANSWERED");
            assertThat(audit.toString()).doesNotContain("private token");
        }
        verify(client, times(5)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test void writesPairedResultsAndSummaryWithoutMutatingOriginalStatus() throws Exception {
        var run = new RagAuditEvaluation.Run(directory, "simulated");
        var row = json.createObjectNode().put("caseId", "case-01").put("number", 1).put("language", "es")
                .put("httpLatencyMs", 100).put("ragOutcome", "PASS");
        row.set("httpResponse", RagAuditEvaluation.safeResponse(rag("ANSWERED")));
        row.set("audit", json.createObjectNode().put("outcome", "COMPLETED").put("proposedDowngrade", true)
                .put("appliedDowngrade", true).put("httpLatencyMs", 50).put("durationMs", 40));
        run.save(row);
        assertThat(Files.readString(directory.resolve("simulated/es-01.json"))).doesNotContain(token);
        var summary = json.readTree(directory.resolve("simulated/summary.json").toFile());
        assertThat(summary.path("ragStatuses").path("ANSWERED").asInt()).isEqualTo(1);
        assertThat(summary.path("appliedDowngrades").asInt()).isEqualTo(1);
        assertThat(summary.path("meanRagHttpMs").asDouble()).isEqualTo(100);
        assertThat(summary.path("meanAuditHttpMs").asDouble()).isEqualTo(50);
        assertThatThrownBy(() -> new RagAuditEvaluation.Run(directory, "simulated").initialize())
                .isInstanceOf(FileAlreadyExistsException.class);
    }
}
