package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.function.Consumer;

/** Small file exporter for explicitly enabled, controlled evaluation runs only. */
public final class RagEvidenceExporter implements Consumer<RagEvidenceSnapshot> {
    private final Path directory;
    private final ObjectMapper json;

    public RagEvidenceExporter(Path directory, ObjectMapper json) {
        this.directory = directory;
        this.json = json;
    }

    @Override
    public void accept(RagEvidenceSnapshot snapshot) {
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(UUID.randomUUID() + ".json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsBytes(snapshot), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            // A controlled evaluation must not silently succeed after losing its evidence.
            throw new UncheckedIOException("Could not export RAG evaluation evidence", e);
        }
    }
}
