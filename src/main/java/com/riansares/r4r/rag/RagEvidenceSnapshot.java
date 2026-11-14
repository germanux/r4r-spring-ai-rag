package com.riansares.r4r.rag;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.config.RetrievalTranslationMode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Opt-in evaluation data, never part of the public HTTP response or operational logs. */
public record RagEvidenceSnapshot(
        Instant capturedAt, String question, String responseLanguage,
        String answer, AnswerStatus status, boolean abstained, List<Citation> citations,
        Provider provider, boolean generationInvoked, String generatedOutput, String outcome,
        int retrievedChunkCount, List<Evidence> chunks, Map<String, Object> metrics, Configuration configuration) {

    public record Provider(String name, String model) { }
    public record Configuration(int topK, double minScore, RetrievalTranslationMode translationMode) { }
    public record Evidence(String label, String source, List<String> headingPath, int ordinal,
                           String content, boolean cited, boolean referencedInGeneratedOutput) { }

    static RagEvidenceSnapshot capture(String question, String language, RagResult result,
            List<MarkdownChunk> delivered, String generatedOutput, RagProgress progress,
            String outcome, int retrievedCount, int topK, double minScore, RetrievalTranslationMode mode) {
        var used = progress.usedProvider();
        var cited = result.citations().stream().map(Citation::label).collect(java.util.stream.Collectors.toSet());
        var chunks = IntStream.range(0, delivered.size()).mapToObj(i -> {
            var chunk = delivered.get(i);
            String label = "[S" + (i + 1) + "]";
            return new Evidence(label, chunk.source(), chunk.headingPath(), chunk.index(), chunk.content(),
                    cited.contains(label), generatedOutput != null && generatedOutput.contains(label));
        }).toList();
        return new RagEvidenceSnapshot(Instant.now(), question, language, result.answer(), result.status(),
                result.abstention(), result.citations(), used == null ? null : new Provider(used.name(), used.model()),
                !delivered.isEmpty(), generatedOutput, outcome, retrievedCount, chunks,
                java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(progress.metrics())),
                new Configuration(topK, minScore, mode));
    }
}
