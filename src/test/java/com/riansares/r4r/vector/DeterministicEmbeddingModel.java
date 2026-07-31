package com.riansares.r4r.vector;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic test-only embedding model. It performs no network calls.
 */
public final class DeterministicEmbeddingModel
        implements EmbeddingModel {

    public static final int DIMENSIONS = 768;

    private static final Pattern TOKEN =
            Pattern.compile("[\\p{L}\\p{N}]+");

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        List<Embedding> embeddings =
                new ArrayList<>(inputs.size());

        for (int index = 0; index < inputs.size(); index++) {
            embeddings.add(new Embedding(
                    vectorFor(inputs.get(index)),
                    index));
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorFor(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private static float[] vectorFor(String text) {
        float[] vector = new float[DIMENSIONS];
        Matcher matcher = TOKEN.matcher(
                text == null ? "" : text.toLowerCase(Locale.ROOT));

        while (matcher.find()) {
            String token = matcher.group();
            int first = Math.floorMod(token.hashCode(), DIMENSIONS);
            int second = Math.floorMod(
                    Integer.rotateLeft(token.hashCode(), 13),
                    DIMENSIONS);

            vector[first] += 1.0f;
            vector[second] += 0.5f;
        }

        double squaredNorm = 0.0;
        for (float value : vector) {
            squaredNorm += value * value;
        }

        if (squaredNorm == 0.0) {
            vector[0] = 1.0f;
            return vector;
        }

        float norm = (float) Math.sqrt(squaredNorm);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= norm;
        }

        return vector;
    }
}
