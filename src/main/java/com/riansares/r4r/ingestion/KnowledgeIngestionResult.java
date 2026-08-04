package com.riansares.r4r.ingestion;

import java.util.Objects;

/**
 * Immutable result of a knowledge ingestion operation.
 *
 * @param discoveredSources total number of discovery sources found in the configured root directory
 * @param changedSources     number of sources that were actually updated (checksum differs)
 * @param unchangedSources   number of sources skipped because checksum matches existing record
 * @param deletedSources     number of sources removed from storage (unsupported, always zero for now)
 * @param persistedChunks    total chunk count persisted across all changed sources
 * @param durationMs         ingestion duration in milliseconds
 */
public record KnowledgeIngestionResult(
        int discoveredSources,
        int changedSources,
        int unchangedSources,
        int deletedSources,
        int persistedChunks,
        long durationMs) {

    public KnowledgeIngestionResult {
        if (discoveredSources < 0) {
            throw new IllegalArgumentException("discoveredSources must be non-negative");
        }
        if (changedSources < 0) {
            throw new IllegalArgumentException("changedSources must be non-negative");
        }
        if (unchangedSources < 0) {
            throw new IllegalArgumentException("unchangedSources must be non-negative");
        }
        if (deletedSources < 0) {
            throw new IllegalArgumentException("deletedSources must be non-negative");
        }
        if (persistedChunks < 0) {
            throw new IllegalArgumentException("persistedChunks must be non-negative");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be non-negative");
        }
    }

}
