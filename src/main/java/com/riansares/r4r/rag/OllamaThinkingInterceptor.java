package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import java.io.IOException;

/** Spring AI 1.0 has no ChatRequest.think field; retain its client and options. */
final class OllamaThinkingInterceptor implements ClientHttpRequestInterceptor {
    private final ObjectMapper json = new ObjectMapper();
    private final boolean think;

    OllamaThinkingInterceptor(boolean think) {
        this.think = think;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (!request.getURI().getPath().endsWith("/api/chat")) {
            return execution.execute(request, body);
        }
        ObjectNode payload = (ObjectNode) json.readTree(body);
        String model = payload.path("model").asText();
        if (!model.equals("qwen3") && !model.startsWith("qwen3:")) {
            return execution.execute(request, body);
        }
        payload.put("think", think);
        byte[] encoded = json.writeValueAsBytes(payload);
        request.getHeaders().setContentLength(encoded.length);
        return execution.execute(request, encoded);
    }
}
