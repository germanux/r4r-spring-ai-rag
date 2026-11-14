package com.riansares.r4r.llm_tests;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Bounded retries for explicit temporary rejections; never retries ambiguous transport failures. */
final class RagTestHttpClient {
    private RagTestHttpClient() { }

    @FunctionalInterface
    interface Sleeper { void sleep(long millis) throws InterruptedException; }

    static HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        return send(client, request, Thread::sleep, Clock.systemUTC());
    }

    static HttpResponse<String> send(HttpClient client, HttpRequest request, Sleeper sleeper, Clock clock)
            throws Exception {
        return send(client, request, sleeper, clock, true);
    }

    static HttpResponse<String> sendAudit(HttpClient client, HttpRequest request) throws Exception {
        return send(client, request, Thread::sleep, Clock.systemUTC(), false);
    }

    static HttpResponse<String> send(HttpClient client, HttpRequest request, Sleeper sleeper, Clock clock, boolean retry503)
            throws Exception {
        for (int attempt = 1; ; attempt++) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if ((status != 429 && !(retry503 && status == 503)) || attempt == 4) return response;
            long delay = retryDelay(response.headers().firstValue("Retry-After").orElse("2"), clock);
            System.out.printf("HTTP %d: retry %d/3 in %.1f seconds%n", status, attempt, delay / 1000.0);
            sleeper.sleep(delay);
        }
    }

    static long retryDelay(String value, Clock clock) {
        try {
            return Math.max(2000, Math.multiplyExact(Long.parseLong(value.trim()), 1000));
        } catch (NumberFormatException ignored) {
            try {
                var until = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Math.max(2000, Duration.between(clock.instant(), until).toMillis());
            } catch (java.time.DateTimeException | ArithmeticException invalid) {
                return 2000;
            }
        } catch (ArithmeticException invalid) {
            return 2000;
        }
    }
}
