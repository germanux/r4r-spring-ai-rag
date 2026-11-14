package com.riansares.r4r.llm_tests;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagTestHttpClientTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC);
    private final HttpClient client = mock(HttpClient.class);
    private final HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost/test")).build();

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String retryAfter) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(
                retryAfter == null ? Map.of() : Map.of("Retry-After", List.of(retryAfter)), (a, b) -> true));
        return response;
    }

    @Test void retriesTemporaryRejectionsUsingServerDelayThenDefaultTwoSeconds() throws Exception {
        var success = response(200, null);
        var limited = response(429, "5");
        var busy = response(503, null);
        when(client.send(eq(request), any(HttpResponse.BodyHandler.class)))
                .thenReturn(limited, busy, success);
        var delays = new ArrayList<Long>();
        assertThat(RagTestHttpClient.send(client, request, delays::add, clock)).isSameAs(success);
        assertThat(delays).containsExactly(5000L, 2000L);
        verify(client, times(3)).send(eq(request), any(HttpResponse.BodyHandler.class));
    }

    @Test void stopsAfterFourAttemptsWithoutSleepingAfterTheLastRejection() throws Exception {
        var limited = response(429, "invalid");
        when(client.send(eq(request), any(HttpResponse.BodyHandler.class))).thenReturn(limited);
        var delays = new ArrayList<Long>();
        assertThat(RagTestHttpClient.send(client, request, delays::add, clock)).isSameAs(limited);
        assertThat(delays).containsExactly(2000L, 2000L, 2000L);
        verify(client, times(4)).send(eq(request), any(HttpResponse.BodyHandler.class));
    }

    @Test void ordinaryFailuresAreNotRetried() throws Exception {
        var failed = response(500, null);
        when(client.send(eq(request), any(HttpResponse.BodyHandler.class))).thenReturn(failed);
        assertThat(RagTestHttpClient.send(client, request, delay -> fail("Unexpected sleep"), clock)).isSameAs(failed);
        verify(client).send(eq(request), any(HttpResponse.BodyHandler.class));
    }

    @Test void transportFailuresAreNotRetriedToAvoidDuplicateGeneration() throws Exception {
        when(client.send(eq(request), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("connection lost"));
        assertThatThrownBy(() -> RagTestHttpClient.send(client, request, delay -> fail("Unexpected sleep"), clock))
                .isInstanceOf(java.io.IOException.class);
        verify(client).send(eq(request), any(HttpResponse.BodyHandler.class));
    }

    @Test void supportsHttpDatesAndSafeFallbacks() {
        assertThat(RagTestHttpClient.retryDelay("Fri, 13 Nov 2026 12:00:07 GMT", clock)).isEqualTo(7000);
        for (String value : List.of("0", "-1", "nonsense", "9223372036854775807",
                "Fri, 13 Nov 2026 11:59:00 GMT")) {
            assertThat(RagTestHttpClient.retryDelay(value, clock)).isEqualTo(2000);
        }
        assertThat(RagLiveQuestionsIT.SLEEP_WAIT).isEqualTo(2000);
    }

    @Test void auditRetriesOnly429AndNeverReplaysA503WithAConsumedToken() throws Exception {
        var busy = response(503, "5");
        when(client.send(eq(request), any(HttpResponse.BodyHandler.class))).thenReturn(busy);
        assertThat(RagTestHttpClient.send(client, request, delay -> fail("Do not replay an audit on 503"), clock, false)).isSameAs(busy);
        verify(client, times(1)).send(eq(request), any(HttpResponse.BodyHandler.class));
        clearInvocations(client);
        var limited = response(429, "3"); var success = response(200, null);
        when(client.send(eq(request), any(HttpResponse.BodyHandler.class))).thenReturn(limited, success);
        var delays = new ArrayList<Long>();
        assertThat(RagTestHttpClient.send(client, request, delays::add, clock, false)).isSameAs(success);
        assertThat(delays).containsExactly(3000L);
        verify(client, times(2)).send(eq(request), any(HttpResponse.BodyHandler.class));
    }
}
