package com.riansares.r4r.rag.api;

import java.util.List;

public record RagQueryResponse(
        String answer,
        boolean abstained,
        List<Citation> citations) {

    public record Citation(
            String label,
            String source,
            List<String> headingPath,
            int ordinal) {
    }
}
