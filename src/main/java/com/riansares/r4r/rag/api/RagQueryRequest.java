package com.riansares.r4r.rag.api;

/** Request for a RAG answer; the search text and response language are optional. */
public record RagQueryRequest(String question, String retrievalQuestion, String responseLanguage) {
    public RagQueryRequest(String question) {
        this(question, null, null);
    }

    /** UTF-16 units, matching HTML maxlength; reject rather than truncate input. */
    static boolean isValid(RagQueryRequest request) {
        return request != null && request.question != null && !request.question.isBlank()
                && request.question.length() <= 700
                && (request.retrievalQuestion == null || request.retrievalQuestion.length() <= 2000)
                && (request.responseLanguage == null || request.responseLanguage.isBlank()
                    || request.responseLanguage.equalsIgnoreCase("es")
                    || request.responseLanguage.equalsIgnoreCase("en"));
    }
}
