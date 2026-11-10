package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.config.NomicRerankProperties;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded BM25 lexical reranking over cosine-qualified candidates, shared by PG and S3/Nomic. */
public final class NomicCandidateReranker {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern TERM = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final double K1 = 1.2, B = .75;
    private final NomicRerankProperties config;
    public record Candidate(MarkdownChunk chunk, double similarity) { }
    public NomicCandidateReranker(NomicRerankProperties config) { this.config = Objects.requireNonNull(config); }
    public int candidateCount(int topK) { return (int)Math.min(10_000L, (long)topK * config.candidateMultiplier()); }
    public List<MarkdownChunk> select(String query, List<Candidate> candidates, int topK) {
        if (candidates.size() <= topK || config.lexicalWeight() == 0)
            return candidates.stream().limit(topK).map(Candidate::chunk).toList();
        var terms = new HashSet<>(tokens(query));
        var frequencies = new ArrayList<Map<String, Integer>>();
        var documentFrequency = new HashMap<String, Integer>();
        var lengths = new ArrayList<Integer>();
        double averageLength = 0;
        for (var candidate : candidates) {
            if (!Double.isFinite(candidate.similarity()) || candidate.similarity() < -1 || candidate.similarity() > 1)
                throw new IllegalStateException("Invalid Nomic candidate similarity");
            var words = tokens(String.join(" ", candidate.chunk().headingPath()) + "\n" + candidate.chunk().content());
            var counts = new HashMap<String, Integer>();
            words.forEach(word -> counts.merge(word, 1, Integer::sum)); frequencies.add(counts);
            counts.keySet().forEach(word -> documentFrequency.merge(word, 1, Integer::sum));
            lengths.add(words.size()); averageLength += words.size();
        }
        averageLength = Math.max(1, averageLength / candidates.size());
        double[] lexical = new double[candidates.size()]; double maximum = 0;
        for (int i = 0; i < candidates.size(); i++) {
            for (String term : terms) {
                int frequency = frequencies.get(i).getOrDefault(term, 0);
                if (frequency == 0) continue;
                int df = documentFrequency.get(term);
                double idf = Math.log(1 + (candidates.size() - df + .5) / (df + .5));
                lexical[i] += idf * frequency * (K1 + 1)
                        / (frequency + K1 * (1 - B + B * lengths.get(i) / averageLength));
            }
            maximum = Math.max(maximum, lexical[i]);
        }
        if (maximum == 0) return candidates.stream().limit(topK).map(Candidate::chunk).toList();
        final double normalizer = maximum;
        var positions = new ArrayList<Integer>(); for (int i = 0; i < candidates.size(); i++) positions.add(i);
        // Keep raw cosine separate: this fused ranking value is never used as a confidence threshold.
        positions.sort(Comparator.<Integer>comparingDouble(i ->
                candidates.get(i).similarity() + config.lexicalWeight() * lexical[i] / normalizer).reversed()
                .thenComparingInt(i -> i));
        return positions.stream().limit(topK).map(i -> candidates.get(i).chunk()).toList();
    }
    private static List<String> tokens(String value) {
        String normalized = MARKS.matcher(Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("");
        var matcher = TERM.matcher(normalized); var result = new ArrayList<String>();
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
}
