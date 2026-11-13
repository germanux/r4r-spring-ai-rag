package com.riansares.r4r.rag;

import java.util.Locale;

/** Backward-compatible values of RAG_OPENAI_ENABLED. */
public enum OpenAiMode {
    DISABLED, FALLBACK, PRIMARY;

    public static OpenAiMode parse(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "false" -> DISABLED;
            case "true" -> FALLBACK;
            case "primary" -> PRIMARY;
            default -> throw new IllegalArgumentException("RAG_OPENAI_ENABLED must be false, true or primary");
        };
    }
}
