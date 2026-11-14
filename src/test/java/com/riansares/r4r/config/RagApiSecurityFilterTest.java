package com.riansares.r4r.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagApiSecurityFilterTest {

    @Test void auditRequiresAllowedOriginWithoutConsumingASecondAnswerInterval() throws Exception {
        var filter = new RagApiSecurityFilter(properties(true, false));
        assertEquals(200, execute(filter, "/api/rag/answers", "10.0.0.8", "http://localhost:63342").getStatus());
        assertEquals(403, execute(filter, "/api/rag/audits/123", "10.0.0.8", "https://untrusted.example").getStatus());
        assertEquals(200, execute(filter, "/api/rag/audits/123", "10.0.0.8", "http://localhost:63342").getStatus());
        assertEquals(429, execute(filter, "/api/rag/answers", "10.0.0.8", "http://localhost:63342").getStatus());
    }

    private static final List<String> ALLOWED_ORIGINS = List.of(
            "https://athagon.tech",
            "http://localhost:63342",
            "http://127.0.0.1:63342",
            "http://192.168.1.203:8088");

    @ParameterizedTest
    @ValueSource(strings = {"/api/rag/answers", "/api/rag/answers/stream"})
    void allowsSynologyWebStationOrigin(String path) throws Exception {
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, false));

        MockHttpServletResponse response = execute(filter, path, "192.168.1.20", "http://192.168.1.203:8088");

        assertEquals(200, response.getStatus());
        assertEquals("http://192.168.1.203:8088", response.getHeader("Access-Control-Allow-Origin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rag/answers", "/api/rag/answers/stream"})
    void allowsFirstRequestAndBlocksSecondRequestFromSameIpWithinWindow(String path) throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, false), clock::get);

        MockHttpServletResponse firstResponse = execute(filter, path, "10.0.0.8", "http://localhost:63342");
        assertEquals(200, firstResponse.getStatus());

        clock.addAndGet(Duration.ofSeconds(5).toNanos());
        MockHttpServletResponse secondResponse = execute(filter, path, "10.0.0.8", "http://localhost:63342");

        assertEquals(429, secondResponse.getStatus());
        assertEquals("15", secondResponse.getHeader("Retry-After"));
        assertEquals("http://localhost:63342", secondResponse.getHeader("Access-Control-Allow-Origin"));
        assertTrue(secondResponse.getContentAsString().contains("rate_limited"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rag/answers", "/api/rag/answers/stream"})
    void allowsSameTimeRequestFromDifferentIp(String path) throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, false), clock::get);

        assertEquals(200, execute(filter, path, "10.0.0.8", "http://localhost:63342").getStatus());
        assertEquals(200, execute(filter, path, "10.0.0.9", "http://localhost:63342").getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rag/answers", "/api/rag/answers/stream"})
    void rejectsDisallowedOrigin(String path) throws Exception {
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, false));

        MockHttpServletResponse response = execute(filter, path, "10.0.0.8", "https://evil.example");

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("origin_not_allowed"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rag/answers", "/api/rag/answers/stream"})
    void rejectsMissingOriginWhenRequired(String path) throws Exception {
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, false));

        MockHttpServletResponse response = execute(filter, path, "10.0.0.8", null);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("origin_required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rag/answers", "/api/rag/answers/stream"})
    void trustsCloudflareIpOnlyWhenProxyHeadersAreEnabled(String path) throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, true), clock::get);

        MockHttpServletRequest first = request(path, "10.0.0.2", "http://localhost:63342");
        first.addHeader("CF-Connecting-IP", "203.0.113.10");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());
        assertEquals(200, firstResponse.getStatus());

        MockHttpServletRequest second = request(path, "10.0.0.2", "http://localhost:63342");
        second.addHeader("CF-Connecting-IP", "203.0.113.11");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());
        assertEquals(200, secondResponse.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rag/answers", "/api/rag/answers/stream"})
    void optionsPreflightDoesNotConsumeRateLimit(String path) throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, false), clock::get);

        MockHttpServletRequest options = new MockHttpServletRequest("OPTIONS", path);
        options.setRemoteAddr("10.0.0.8");
        options.addHeader("Origin", "http://localhost:63342");
        MockHttpServletResponse optionsResponse = new MockHttpServletResponse();
        filter.doFilter(options, optionsResponse, new MockFilterChain());
        assertEquals(200, optionsResponse.getStatus());

        assertEquals(200, execute(filter, path, "10.0.0.8", "http://localhost:63342").getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rag/answers", "/api/rag/answers/stream"})
    void globalLimitCannotBeBypassedByChangingIpAndResetsAfterOneMinute(String path) throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        RagApiSecurityProperties strict = new RagApiSecurityProperties(
                true, ALLOWED_ORIGINS, Duration.ofSeconds(20), 2, true, true, 10_000);
        RagApiSecurityFilter filter = new RagApiSecurityFilter(strict, clock::get);

        assertEquals(200, execute(filter, path, "10.0.0.8", "http://localhost:63342").getStatus());
        assertEquals(200, execute(filter, path, "10.0.0.9", "http://localhost:63342").getStatus());
        MockHttpServletResponse blocked = execute(filter, path, "10.0.0.10", "http://localhost:63342");
        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentAsString().contains("global_rate_limited"));
        assertEquals("60", blocked.getHeader("Retry-After"));

        clock.addAndGet(Duration.ofMinutes(1).toNanos());
        assertEquals(200, execute(filter, path, "10.0.0.10", "http://localhost:63342").getStatus());
    }

    @Test
    void restAndStreamCannotAlternateToBypassIpOrGlobalQuotas() throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        var filter = new RagApiSecurityFilter(properties(true, false), clock::get);
        assertEquals(200, execute(filter, "/api/rag/answers", "10.0.0.8", "https://athagon.tech").getStatus());
        var blocked = execute(filter, "/api/rag/answers/stream", "10.0.0.8", "https://athagon.tech");
        assertEquals(429, blocked.getStatus());
        assertEquals("20", blocked.getHeader("Retry-After"));
        assertTrue(blocked.getHeader("Access-Control-Expose-Headers").contains("Retry-After"));
        var global = new RagApiSecurityFilter(new RagApiSecurityProperties(true, ALLOWED_ORIGINS,
                Duration.ZERO, 1, true, false, 100), clock::get);
        assertEquals(200, execute(global, "/api/rag/answers", "10.0.0.8", "https://athagon.tech").getStatus());
        var globalBlocked = execute(global, "/api/rag/answers/stream", "10.0.0.9", "https://athagon.tech");
        assertEquals(429, globalBlocked.getStatus());
        assertEquals("60", globalBlocked.getHeader("Retry-After"));
        assertTrue(globalBlocked.getContentAsString().contains("global_rate_limited"));
    }

    @Test
    void warmupHasIndependentQuotaEvenForEncodedMvcPath() throws Exception {
        var filter = new RagApiSecurityFilter(new RagApiSecurityProperties(true, ALLOWED_ORIGINS,
                Duration.ofSeconds(20), 1, true, false, 100));
        for (int i = 0; i < 3; i++) assertEquals(200, execute(filter, "/api/rag/%77armup", "10.0.0.8", "https://athagon.tech").getStatus());
        assertEquals(200, execute(filter, "/api/rag/answers/stream", "10.0.0.8", "https://athagon.tech").getStatus());
        assertEquals(200, execute(filter, "/api/rag/warmup", "10.0.0.8", "https://athagon.tech").getStatus());
        assertEquals(429, execute(filter, "/api/rag/answers", "10.0.0.8", "https://athagon.tech").getStatus());
        assertEquals(429, execute(filter, "/api/rag/answers/stream", "10.0.0.9", "https://athagon.tech").getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rag/answers", "/api/rag/answers/stream"})
    void capacityLimitAlsoReturnsRetryAfterWithoutLeakingClientDetails(String path) throws Exception {
        var clock = new AtomicLong(1_000_000_000L);
        var filter = new RagApiSecurityFilter(new RagApiSecurityProperties(true, ALLOWED_ORIGINS,
                Duration.ofSeconds(20), 12, true, false, 1), clock::get);
        assertEquals(200, execute(filter, path, "10.0.0.8", "https://athagon.tech").getStatus());
        var blocked = execute(filter, path, "10.0.0.9", "https://athagon.tech");
        assertEquals(429, blocked.getStatus());
        assertEquals("20", blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("rate_limit_capacity"));
        assertTrue(!blocked.getContentAsString().contains("10.0.0."));
    }

    private static RagApiSecurityProperties properties(boolean requireOrigin, boolean trustProxyHeaders) {
        return new RagApiSecurityProperties(
                true,
                ALLOWED_ORIGINS,
                Duration.ofSeconds(20),
                12,
                requireOrigin,
                trustProxyHeaders,
                10_000);
    }

    private static MockHttpServletResponse execute(
            RagApiSecurityFilter filter,
            String path,
            String remoteIp,
            String origin) throws Exception {

        MockHttpServletRequest request = request(path, remoteIp, origin);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest request(String path, String remoteIp, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteIp);
        request.setContentType("application/json");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }
}
