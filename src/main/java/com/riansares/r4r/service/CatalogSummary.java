package com.riansares.r4r.service;

import java.util.List;

public record CatalogSummary(int documentCount, int chunkCount, long totalCharacters, List<String> sources) {
    public CatalogSummary {
        sources = List.copyOf(sources);
    }
}
