package com.riansares.r4r.rag;

/** Public review only: never contains replacement answer text, chunks or provider addresses. */
public record RagAuditResult(AnswerStatus originalStatus, AnswerStatus recommendedStatus,
                             AnswerStatus effectiveStatus, boolean conclusive, String note,
                             @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                             Metadata metadata) {
    public record Metadata(String verdict, String reason, String provider, String model, Double durationMs, String outcome) { }
    public RagAuditResult(AnswerStatus original, AnswerStatus recommended, AnswerStatus effective, boolean conclusive, String note) {
        this(original, recommended, effective, conclusive, note, null);
    }
    public RagAuditResult withMetadata(Metadata details) {
        return new RagAuditResult(originalStatus, recommendedStatus, effectiveStatus, conclusive, note, details);
    }
    public static RagAuditResult inconclusive(AnswerStatus original) {
        return new RagAuditResult(original, original, original, false, "");
    }
}
