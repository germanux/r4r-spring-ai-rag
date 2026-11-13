package com.riansares.r4r.config;

import java.util.Locale;

/** NEVER: direct; ALWAYS: translate EN first; AUTO: direct, then translate EN only on empty retrieval. */
public enum RetrievalTranslationMode {
    AUTO, ALWAYS, NEVER;

    public static RetrievalTranslationMode parse(String value) {
        try { return valueOf(value.strip().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException invalid) {
            throw new IllegalArgumentException("retrieval.translation.mode must be auto, always or never", invalid);
        }
    }
}
