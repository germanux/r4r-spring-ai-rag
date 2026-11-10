package com.riansares.r4r.titan;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

/** Titan-specific adapter shared by retrieval and the importer. No chunking or Nomic conversion. */
final class TitanEmbeddingClient {
    static final ObjectMapper JSON = new ObjectMapper();
    private final BedrockRuntimeClient client;
    TitanEmbeddingClient(BedrockRuntimeClient client) { this.client = Objects.requireNonNull(client); }
    List<Float> embed(String text) throws IOException {
        var response = client.invokeModel(InvokeModelRequest.builder().modelId(RetrievalHandler.MODEL)
                .contentType("application/json").accept("application/json").body(SdkBytes.fromByteArray(
                        JSON.writeValueAsBytes(Map.of("inputText", text, "dimensions", 1024, "normalize", true)))).build());
        return decode(JSON.readTree(response.body().asByteArray()));
    }
    static List<Float> decode(JsonNode body) {
        var values = body.path("embedding");
        if (!values.isArray() || values.size() != 1024) throw new IllegalStateException("Invalid embedding dimensions");
        List<Float> result = new ArrayList<>(1024); double norm = 0;
        for (var value : values) {
            if (!value.isNumber() || !Float.isFinite(value.floatValue())) throw new IllegalStateException("Invalid embedding value");
            float number = value.floatValue(); result.add(number); norm += (double)number * number;
        }
        if (norm == 0) throw new IllegalStateException("Zero embedding");
        return result;
    }
}
