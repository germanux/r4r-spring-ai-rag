package com.riansares.r4r.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OllamaThinkingInterceptorTest {
    @Test void honorsNativeThinkingFlagAndPreservesSpringAiPayload() throws Exception {
        var json = new ObjectMapper();
        for (boolean think : new boolean[]{false, true}) {
            var request = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://local/api/chat"));
            var execution = mock(ClientHttpRequestExecution.class);
            byte[] body = "{\"model\":\"qwen3:8b\",\"messages\":[{\"role\":\"user\",\"content\":\"Question\"}],\"keep_alive\":\"3h\",\"stream\":false,\"options\":{\"temperature\":0.2}}".getBytes(StandardCharsets.UTF_8);
            doAnswer(call -> {
                byte[] sent = call.getArgument(1);
                var payload = json.readTree(sent);
                assertThat(payload.path("think").asBoolean()).isEqualTo(think);
                assertThat(payload.path("messages")).isEqualTo(json.readTree(body).path("messages"));
                assertThat(payload.path("keep_alive").asText()).isEqualTo("3h");
                assertThat(payload.path("options")).isEqualTo(json.readTree(body).path("options"));
                assertThat(request.getHeaders().getContentLength()).isEqualTo(sent.length);
                return null;
            }).when(execution).execute(same(request), any(byte[].class));
            new OllamaThinkingInterceptor(think).intercept(request, body, execution);
            verify(execution, times(1)).execute(same(request), any(byte[].class));
        }
    }

    @Test void leavesOtherModelsAndOllamaOperationsUntouched() throws Exception {
        for (String path : new String[]{"/api/chat", "/api/ps"}) {
            var request = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://local" + path));
            var execution = mock(ClientHttpRequestExecution.class);
            byte[] body = "{\"model\":\"qwen2.5:7b\"}".getBytes(StandardCharsets.UTF_8);
            new OllamaThinkingInterceptor(false).intercept(request, body, execution);
            verify(execution).execute(same(request), same(body));
        }
    }
}
