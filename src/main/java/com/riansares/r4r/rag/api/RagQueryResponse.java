package com.riansares.r4r.rag.api;

import java.util.List;

public record RagQueryResponse(
        String answer,
        boolean abstained,
        List<Citation> citations, Provider provider) {
    public record Provider(String name, String model, InferenceHost.Details inferenceHost) { }
    public RagQueryResponse(String answer, boolean abstained, List<Citation> citations) { this(answer, abstained, citations, null); }

    public record Citation(
            String label,
            String source,
            List<String> headingPath,
            int ordinal) {
    }
}
