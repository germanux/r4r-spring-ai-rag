package com.riansares.r4r.rag.api;

import java.util.List;

public record RagQueryResponse(
        String answer,
        boolean abstained,
        List<Citation> citations, Provider provider, com.riansares.r4r.rag.AnswerStatus status,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String auditId) {
    public RagQueryResponse(String answer, boolean abstained, List<Citation> citations, Provider provider,
                            com.riansares.r4r.rag.AnswerStatus status) { this(answer, abstained, citations, provider, status, null); }
    public RagQueryResponse withAuditId(String id) { return new RagQueryResponse(answer, abstained, citations, provider, status, id); }
    public RagQueryResponse(String answer, boolean abstained, List<Citation> citations, Provider provider) {
        this(answer, abstained, citations, provider, abstained
                ? com.riansares.r4r.rag.AnswerStatus.ABSTAINED : com.riansares.r4r.rag.AnswerStatus.ANSWERED);
    }
    public record Provider(String name, String model, InferenceHost.Details inferenceHost) { }
    public RagQueryResponse(String answer, boolean abstained, List<Citation> citations) { this(answer, abstained, citations, null); }

    public record Citation(
            String label,
            String source,
            List<String> headingPath,
            int ordinal) {
    }
}
