package com.riansares.r4r.llm_tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.riansares.r4r.evaluation.EvaluationQuestions.QuestionCase;
import com.riansares.r4r.rag.AnswerStatus;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Test-only extension of the live suites: one RAG result and its optional, one-shot audit. */
final class RagAuditEvaluation {
    private static final ObjectMapper JSON = new ObjectMapper();
    private RagAuditEvaluation() { }

    static ObjectNode audit(HttpClient client, String base, JsonNode rag) {
        String original = rag.path("status").asText("");
        var record = JSON.createObjectNode();
        record.put("outcome", "SKIPPED").put("originalStatus", original).put("effectiveStatus", original)
                .put("proposedDowngrade", false).put("appliedDowngrade", false);
        for (String key : List.of("recommendedStatus", "verdict", "reason", "provider", "model", "durationMs", "httpLatencyMs")) record.putNull(key);
        if (rag.path("auditId").isMissingNode() || rag.path("auditId").isNull()) return record.put("skipReason", "no_audit_id");
        String id = rag.path("auditId").asText("");
        if (!id.matches("[a-f0-9-]{36}")) return record.put("outcome", "ERROR").put("error", "invalid_audit_id");
        long started = System.nanoTime();
        try {
            var request = HttpRequest.newBuilder(URI.create(base + "/api/rag/audits/" + id))
                    .timeout(Duration.ofSeconds(100)).header("Accept", "application/json")
                    .header("Origin", "https://athagon.tech").POST(HttpRequest.BodyPublishers.noBody()).build();
            var response = RagTestHttpClient.sendAudit(client, request);
            record.put("httpStatus", response.statusCode());
            if (response.statusCode() == 404) return record.put("skipReason", "audit_unavailable_or_expired");
            if (response.statusCode() != 200) return record.put("outcome", "ERROR").put("error", "audit_http_" + response.statusCode());
            var review = JSON.readTree(response.body());
            if (review == null || !review.isObject()) throw new IllegalArgumentException("invalid_audit_json");
            record.set("response", review);
            var metadata = review.path("metadata");
            for (String key : List.of("recommendedStatus", "note")) record.set(key, review.path(key));
            for (String key : List.of("verdict", "reason", "provider", "model")) {
                if (!metadata.path(key).isMissingNode()) record.set(key, metadata.path(key));
            }
            if (metadata.path("durationMs").isNumber()) record.set("durationMs", metadata.path("durationMs"));
            var originalStatus = AnswerStatus.valueOf(original);
            var recommended = AnswerStatus.valueOf(review.path("recommendedStatus").asText());
            var effective = AnswerStatus.valueOf(review.path("effectiveStatus").asText());
            boolean downgrade = recommended.ordinal() > originalStatus.ordinal();
            record.put("proposedDowngrade", downgrade);
            if (!original.equals(review.path("originalStatus").asText()) || !review.path("conclusive").isBoolean()
                    || !review.path("conclusive").asBoolean())
                return record.put("outcome", "ERROR").put("error", "audit_inconclusive_or_mismatched");
            boolean consistent = effective == (downgrade ? recommended : originalStatus)
                    && metadata.path("verdict").asText().equals(downgrade ? "FAIL" : "PASS")
                    && !metadata.path("reason").asText().isBlank() && !metadata.path("model").asText().isBlank()
                    && metadata.path("durationMs").isNumber() && Double.isFinite(metadata.path("durationMs").asDouble())
                    && metadata.path("durationMs").asDouble() >= 0;
            if (!consistent) return record.put("outcome", "ERROR").put("error", "audit_policy_or_metadata_invalid");
            return record.put("outcome", "COMPLETED").put("effectiveStatus", effective.name()).put("appliedDowngrade", downgrade);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return record.put("outcome", "ERROR").put("error", "InterruptedException");
        } catch (Exception failure) {
            return record.put("outcome", "ERROR").put("error", failure.getClass().getSimpleName());
        } finally { record.put("httpLatencyMs", (System.nanoTime() - started) / 1_000_000.0); }
    }

    static JsonNode safeResponse(JsonNode response) {
        var copy = response.deepCopy();
        if (copy.isObject()) ((ObjectNode) copy).remove("auditId");
        return copy;
    }
    static String safeResponse(String response) {
        try { return safeResponse(JSON.readTree(response)).toString(); }
        catch (Exception invalid) { return "<non-JSON response>"; }
    }

    private static class Current {
        static final Run RUN = new Run(Path.of(System.getProperty("r4r.test.results-dir", "docs/evaluation/runs")),
                System.getProperty("r4r.test.run-id", "llm-audit-" + Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID().toString().substring(0, 8)));
    }
    static void prepare() throws java.io.IOException { Current.RUN.initialize(); }
    static void record(HttpClient client, String base, String language, int number, QuestionCase test,
                       HttpResponse<String> response, double elapsedMs, boolean passed) {
        try {
            var rag = JSON.readTree(response.body());
            if (rag == null) rag = JSON.createObjectNode();
            var audit = response.statusCode() == 200 ? audit(client, base, rag)
                    : JSON.createObjectNode().put("outcome", "SKIPPED").put("skipReason", "rag_request_failed");
            var record = JSON.createObjectNode();
            record.put("caseId", test.caseId()).put("number", number).put("language", language).put("question", test.question())
                    .put("httpStatus", response.statusCode()).put("httpLatencyMs", elapsedMs).put("ragOutcome", passed ? "PASS" : "FAIL")
                    .put("auditIdPresent", rag.hasNonNull("auditId"));
            record.set("httpResponse", safeResponse(rag)); record.set("audit", audit);
            Current.RUN.save(record);
            System.out.printf("AUDIT: %s%n", audit);
        } catch (Exception invalid) {
            // Never obscure an existing RAG assertion with an audit/export exception.
            System.err.printf("Evaluation export ERROR: %s%n", invalid.getClass().getSimpleName());
        }
    }
    static void summary() { Current.RUN.printSummary(); }

    static final class Run {
        private final Path directory;
        private final String id;
        private boolean created;
        private final List<ObjectNode> records = new ArrayList<>();
        Run(Path root, String id) {
            if (id.isBlank() || !Path.of(id).getFileName().toString().equals(id) || Set.of(".", "..").contains(id))
                throw new IllegalArgumentException("run-id must be a single directory name");
            this.directory = root.resolve(id); this.id = id;
        }
        synchronized void initialize() throws java.io.IOException {
            if (!created) { Files.createDirectories(directory.getParent()); Files.createDirectory(directory); created = true; }
        }
        synchronized void save(ObjectNode record) throws java.io.IOException {
            initialize();
            record.put("runId", id);
            Files.writeString(directory.resolve("%s-%02d.json".formatted(record.path("language").asText(), record.path("number").asInt())),
                    JSON.writerWithDefaultPrettyPrinter().writeValueAsString(record) + "\n", StandardOpenOption.CREATE_NEW);
            records.add(record);
            Files.writeString(directory.resolve("summary.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(summary()) + "\n");
        }
        synchronized ObjectNode summary() {
            var result = JSON.createObjectNode(); result.put("runId", id).put("total", records.size());
            var ragStatuses = result.putObject("ragStatuses"); var ragChecks = result.putObject("ragChecks");
            int proposed = 0, applied = 0, errors = 0, skipped = 0;
            for (var row : records) {
                String state = row.path("httpResponse").path("status").asText("ERROR"), check = row.path("ragOutcome").asText();
                ragStatuses.put(state, ragStatuses.path(state).asInt() + 1); ragChecks.put(check, ragChecks.path(check).asInt() + 1);
                var audit = row.path("audit");
                if (audit.path("proposedDowngrade").asBoolean()) proposed++;
                if (audit.path("appliedDowngrade").asBoolean()) applied++;
                if (audit.path("outcome").asText().equals("ERROR")) errors++;
                if (audit.path("outcome").asText().equals("SKIPPED")) skipped++;
            }
            result.put("proposedDowngrades", proposed).put("appliedDowngrades", applied).put("auditFailures", errors).put("auditSkipped", skipped);
            result.put("meanRagHttpMs", records.stream().mapToDouble(r -> r.path("httpLatencyMs").asDouble()).average().orElse(0));
            result.put("meanAuditHttpMs", records.stream().map(r -> r.path("audit").path("httpLatencyMs")).filter(JsonNode::isNumber).mapToDouble(JsonNode::asDouble).average().orElse(0));
            result.put("meanAuditServiceMs", records.stream().map(r -> r.path("audit").path("durationMs")).filter(JsonNode::isNumber).mapToDouble(JsonNode::asDouble).average().orElse(0));
            return result;
        }
        synchronized void printSummary() { System.out.println("RAG + AUDIT SUMMARY: " + summary()); }
    }
}
