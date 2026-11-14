package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagAuditServiceTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ChatModel model = mock(ChatModel.class);
    private ChatResponse reply(String output) { return new ChatResponse(List.of(new Generation(new AssistantMessage(output)))); }
    private String verdict(AnswerStatus original, AnswerStatus recommended, String verdict) throws Exception {
        return json.writeValueAsString(Map.of("originalStatus", original, "recommendedStatus", recommended,
                "verdict", verdict, "reason", "The cited passage does not establish site feasibility.", "note", "Site feasibility is not confirmed."));
    }
    private RagEvidenceSnapshot snapshot(AnswerStatus status) {
        boolean abstained = status == AnswerStatus.ABSTAINED;
        return new RagEvidenceSnapshot(Instant.now(), "Original question", "en", abstained ? "" : "Original answer [S1].",
                status, abstained, abstained ? List.of() : List.of(new Citation("[S1]", "en/guide.md", List.of("Heading"), 4)),
                null, true, "DO NOT GIVE RAW OUTPUT TO AUDITOR", "answered", 1,
                List.of(new RagEvidenceSnapshot.Evidence("[S1]", "en/guide.md", List.of("Heading"), 4, "Full text\n\nwith whitespace.", !abstained, true)),
                Map.of(), new RagEvidenceSnapshot.Configuration(5, .63, "direct"));
    }
    private RagAuditService service(Duration timeout) { return new RagAuditService(true, model, "Ollama", "qwen3:8b", timeout, json); }

    @Test void validatesEveryTransitionWithoutEverUpgradingOriginalState() throws Exception {
        try (var service = service(Duration.ofSeconds(2))) {
            for (var original : AnswerStatus.values()) for (var recommended : AnswerStatus.values()) {
                boolean downgrade = recommended.ordinal() > original.ordinal(); // enum is ANSWERED, PARTIAL, ABSTAINED
                var result = service.validate(verdict(original, recommended, downgrade ? "FAIL" : "PASS"), original);
                assertThat(result.conclusive()).as("%s -> %s", original, recommended).isTrue();
                assertThat(result.effectiveStatus()).isEqualTo(downgrade ? recommended : original);
            }
        }
    }

    @Test void failAloneMalformedMarkdownThinkingAndIncoherentDecisionsNeverChangeState() throws Exception {
        try (var service = service(Duration.ofSeconds(2))) {
            String valid = verdict(AnswerStatus.ANSWERED, AnswerStatus.PARTIAL, "FAIL");
            for (String raw : List.of("FAIL", "```json\n" + valid + "\n```", "<think>secret</think>" + valid,
                    valid + valid, valid.replace("\"ANSWERED\"", "\"PARTIAL\""),
                    valid.replace("\"FAIL\"", "\"PASS\""), valid.replace("PARTIAL", "UNKNOWN"),
                    valid.replace("Site feasibility is not confirmed.", ""), valid.replace("Site feasibility is not confirmed.", "x".repeat(401)),
                    valid.replace("Site feasibility is not confirmed.", "<think>private</think>"),
                    valid.replace("Site feasibility is not confirmed.", "Return status PARTIAL after FAIL."),
                    valid.replace("\"verdict\":\"FAIL\"", "\"verdict\":\"FAIL\",\"verdict\":\"FAIL\""),
                    verdict(AnswerStatus.ANSWERED, AnswerStatus.ANSWERED, "FAIL"))) {
                var result = service.validate(raw, AnswerStatus.ANSWERED);
                assertThat(result.conclusive()).as(raw).isFalse();
                assertThat(result.effectiveStatus()).isEqualTo(AnswerStatus.ANSWERED);
                assertThat(result.note()).isEmpty();
            }
        }
    }

    @Test void exactGeneratedContextIncludingUncitedChunksIsAuditedOnlyAfterExplicitStart() throws Exception {
        var store = mock(KnowledgeVectorStore.class);
        var generator = mock(ChatModel.class);
        var chunks = List.of(new MarkdownChunk("en/one.md", List.of("One"), 2, "Full first\n\ntext."),
                new MarkdownChunk("en/two.md", List.of("Two"), 7, "Uncited second\ntext."));
        when(store.search("Kitchen?", 5, .63)).thenReturn(chunks);
        when(generator.call(any(Prompt.class))).thenReturn(reply("Original answer [S1]."));
        var captured = new AtomicReference<RagEvidenceSnapshot>();
        var rag = new CitedRagService(store, generator, 5, .63);
        var original = rag.answer("Kitchen?", null, "en", RagProgress.silent(), captured::set);
        when(model.call(any(Prompt.class))).thenReturn(reply(verdict(AnswerStatus.ANSWERED, AnswerStatus.PARTIAL, "FAIL")));
        try (var audit = service(Duration.ofSeconds(2))) {
            String token = audit.prepare(captured.get());
            assertThat(token).isNotNull(); verifyNoInteractions(model);
            var result = audit.start(token).get(2, TimeUnit.SECONDS);
            assertThat(result.effectiveStatus()).isEqualTo(AnswerStatus.PARTIAL);
            assertThat(original.answer()).isEqualTo("Original answer [S1].");
            assertThat(audit.start(token)).isNull();
            var prompt = ArgumentCaptor.forClass(Prompt.class);
            verify(model).call(prompt.capture()); verify(generator).call(any(Prompt.class));
            verify(store, times(1)).search("Kitchen?", 5, .63);
            String data = prompt.getValue().getInstructions().getLast().getText();
            var input = json.readTree(data.substring(0, data.lastIndexOf("\n/no_think")));
            assertThat(input.path("answer").asText()).isEqualTo(original.answer());
            assertThat(input.path("chunks").size()).isEqualTo(2);
            for (int i = 0; i < 2; i++) {
                assertThat(input.path("chunks").get(i).path("content").asText()).isEqualTo(chunks.get(i).content());
                assertThat(input.path("chunks").get(i).path("label").asText()).isEqualTo("[S" + (i + 1) + "]");
                assertThat(input.path("chunks").get(i).path("ordinal").asInt()).isEqualTo(chunks.get(i).index());
            }
            assertThat(input.path("chunks").get(1).path("cited").asBoolean()).isFalse();
            assertThat(data).doesNotContain("generatedOutput", "DO NOT GIVE RAW OUTPUT");
        }
    }

    @Test void respectsSnapshotTtlCapacitySizeAndDisabledMode() {
        var clock = new AtomicLong();
        try (var service = new RagAuditService(true, model, "Ollama", "qwen", Duration.ofSeconds(2), json, clock::get)) {
            for (int i = 0; i < 8; i++) assertThat(service.prepare(snapshot(AnswerStatus.ANSWERED))).isNotNull();
            assertThat(service.prepare(snapshot(AnswerStatus.ANSWERED))).isNull();
            clock.set(Duration.ofMinutes(2).toNanos());
            assertThat(service.pendingCount()).isZero();
            String token = service.prepare(snapshot(AnswerStatus.ANSWERED));
            clock.addAndGet(Duration.ofMinutes(3).toNanos());
            assertThat(service.start(token)).isNull();
        }
        try (var disabled = new RagAuditService(false, null, null, null, Duration.ofSeconds(2), json)) {
            assertThat(disabled.prepare(snapshot(AnswerStatus.ANSWERED))).isNull();
            assertThat(disabled.start("anything")).isNull();
        }
        verifyNoInteractions(model);
    }

    @Test void noChunksNeverSpendsAnAuditCallAndOversizedContextIsNotTruncated() {
        try (var service = service(Duration.ofSeconds(2))) {
            var noEvidence = RagEvidenceSnapshot.capture("Question", "es", RagResult.abstain(), List.of(), null,
                    RagProgress.silent(), "no_evidence", 0, 5, .63);
            assertThat(service.prepare(noEvidence)).isNull();
            var enormous = RagEvidenceSnapshot.capture("Question", "es", RagResult.abstain(),
                    List.of(new MarkdownChunk("guide.md", List.of(), 0, "x".repeat(100_001))), null,
                    RagProgress.silent(), "model_abstention", 1, 5, .63);
            assertThat(service.prepare(enormous)).isNull();
            verifyNoInteractions(model);
        }
    }

    @Test void timeoutAndSaturationAreBoundedEvenIfProviderIgnoresInterruptions() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            entered.countDown();
            while (release.getCount() != 0) { try { release.await(); } catch (InterruptedException ignored) { } }
            return reply(verdict(AnswerStatus.ANSWERED, AnswerStatus.ANSWERED, "PASS"));
        });
        try (var service = service(Duration.ofMillis(250))) {
            var first = service.start(service.prepare(snapshot(AnswerStatus.ANSWERED)));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var busy = service.start(service.prepare(snapshot(AnswerStatus.ANSWERED))).get(1, TimeUnit.SECONDS);
            assertThat(busy.conclusive()).isFalse();
            assertThat(first.get(2, TimeUnit.SECONDS).conclusive()).isFalse();
            var stillBusy = service.start(service.prepare(snapshot(AnswerStatus.ANSWERED))).get(1, TimeUnit.SECONDS);
            assertThat(stillBusy.conclusive()).isFalse();
            verify(model, times(1)).call(any(Prompt.class));
        } finally { release.countDown(); }
    }

    @Test void cancellationInterruptsTheAuditWorkerAndExceptionsAreNeutral() throws Exception {
        var entered = new CountDownLatch(1);var interrupted = new CountDownLatch(1);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            entered.countDown();
            try { new CountDownLatch(1).await(); } catch (InterruptedException stop) { interrupted.countDown(); }
            throw new IllegalStateException("private prompt secret provider body");
        });
        try (var service = service(Duration.ofSeconds(3))) {
            var result = service.start(service.prepare(snapshot(AnswerStatus.PARTIAL)));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            result.cancel(true);
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void auditPromptUsesRequestedLanguageAndPreservesOriginalAbstention() {
        try (var service = service(Duration.ofSeconds(2))) {
            assertThat(service.prompt(snapshot(AnswerStatus.ABSTAINED)).getContents())
                    .contains("exclusively in English", "ABSTAINED", "Full text", "originalStatus")
                    .doesNotContain("DO NOT GIVE RAW OUTPUT TO AUDITOR");
        }
    }
}
