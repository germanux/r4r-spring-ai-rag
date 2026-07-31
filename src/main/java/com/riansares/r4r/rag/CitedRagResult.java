package com.riansares.r4r.rag;

import java.util.List;
import java.util.Objects;

public record CitedRagResult(
        String answer,
        boolean abstained,
        List<Citation> citations) {

    public CitedRagResult {
        Objects.requireNonNull(answer, "answer must not be null");
        Objects.requireNonNull(citations, "citations must not be null");
    }

    public record Citation(
            String source,
            List<String> headingPath,
            int ordinal) {

        public Citation {
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(headingPath, "headingPath must not be null");
        }
    }
}
