package com.riansares.r4r.vector.titan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

/** The deployed Lambda wire contract; all AWS responses supplied by tests are simulated. */
public final class TitanResponses {
    public static final ObjectMapper JSON = new ObjectMapper();
    public static ObjectNode body(boolean evidence) {
        var body = JSON.createObjectNode();
        body.set("provider", JSON.valueToTree(Map.of("embedding", "amazon.titan-embed-text-v2:0", "vectorStore", "aws-s3-vectors",
                "index", "r4r-titan-v2-1024", "dimensions", 1024)));
        body.set("metrics", JSON.valueToTree(Map.of("embeddingMs", 3.0, "vectorSearchMs", 2.0, "totalMs", 6.0)));
        body.put("abstained", !evidence); body.put("calibration", "uncalibrated");
        var docs = body.putArray("documents");
        if (evidence) docs.add(JSON.valueToTree(Map.of("id", "original-id", "text", "La empresa reforma viviendas y oficinas.",
                "score", .8, "source", "faq.md", "ordinal", 2, "headingPath", List.of("Servicios"),
                "metadata", Map.of("source", "faq.md", "ordinal", 2, "headingPath", List.of("Servicios"),
                        "source_text", "La empresa reforma viviendas y oficinas."))));
        return body;
    }
    public static InvokeResponse response(ObjectNode body) {
        return InvokeResponse.builder().statusCode(200).payload(SdkBytes.fromUtf8String(body.toString())).build();
    }
}
