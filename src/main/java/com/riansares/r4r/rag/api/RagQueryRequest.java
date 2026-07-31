package com.riansares.r4r.rag.api;

import java.util.List;

/**
 * Request DTO for the RAG query endpoint.
 */
public record RagQueryRequest(String question) {

    public RagQueryRequest {
        // No additional validation here - validation happens in controller
    }
}
