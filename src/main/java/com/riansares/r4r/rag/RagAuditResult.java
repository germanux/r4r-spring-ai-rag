package com.riansares.r4r.rag;

/** Public review only: never contains replacement answer text, chunks or provider addresses. */
public record RagAuditResult(AnswerStatus originalStatus, AnswerStatus recommendedStatus,
                             AnswerStatus effectiveStatus, boolean conclusive, String note) {
    public static RagAuditResult inconclusive(AnswerStatus original) {
        return new RagAuditResult(original, original, original, false, "");
    }
}
