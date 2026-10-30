package com.riansares.r4r.rag.api;

/** Request for a RAG answer; the search text and response language are optional. */
public record RagQueryRequest(String question, String retrievalQuestion, String responseLanguage) {
    public RagQueryRequest(String question) {
        this(question, null, null);
    }
}
