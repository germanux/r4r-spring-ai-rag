package com.riansares.r4r.rag;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result record from the RAG service.
 */
public record RagResult(String answer, boolean abstention, List<Citation> citations) {

    public RagResult {
        answer = Objects.requireNonNull(answer, "answer");
        citations = List.copyOf(Objects.requireNonNull(citations, "citations"));
    }

    /**
     * Creates a non-abstention result with the given answer and citations.
     */
    public static RagResult ofAnswer(String answer, List<Citation> citations) {
        return new RagResult(answer, false, citations);
    }

    /**
     * Creates an abstention result with no citations.
     */
    public static RagResult abstain() {
        return new RagResult("", true, List.of());
    }
}
