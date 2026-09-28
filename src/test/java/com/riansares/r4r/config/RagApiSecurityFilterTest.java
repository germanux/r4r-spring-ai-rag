package com.riansares.r4r.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagApiSecurityFilterTest {

    private static final List<String> ALLOWED_ORIGINS = List.of(
            "https://athagon.tech",
            "http://localhost:63342",
            "http://127.0.0.1:63342");

    @Test
    void allowsFirstRequestAndBlocksSecondRequestFromSameIpWithinWindow() throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, false), clock::get);

        MockHttpServletResponse firstResponse = execute(filter, "10.0.0.8", "http://localhost:63342");
        assertEquals(200, firstResponse.getStatus());

        clock.addAndGet(Duration.ofSeconds(5).toNanos());
        MockHttpServletResponse secondResponse = execute(filter, "10.0.0.8", "http://localhost:63342");

        assertEquals(429, secondResponse.getStatus());
        assertEquals("15", secondResponse.getHeader("Retry-After"));
        assertEquals("http://localhost:63342", secondResponse.getHeader("Access-Control-Allow-Origin"));
        assertTrue(secondResponse.getContentAsString().contains("rate_limited"));
    }

    @Test
    void allowsSameTimeRequestFromDifferentIp() throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, false), clock::get);

        assertEquals(200, execute(filter, "10.0.0.8", "http://localhost:63342").getStatus());
        assertEquals(200, execute(filter, "10.0.0.9", "http://localhost:63342").getStatus());
    }

    @Test
    void rejectsDisallowedOrigin() throws Exception {
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, false));

        MockHttpServletResponse response = execute(filter, "10.0.0.8", "https://evil.example");

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("origin_not_allowed"));
    }

    @Test
    void rejectsMissingOriginWhenRequired() throws Exception {
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, false));

        MockHttpServletResponse response = execute(filter, "10.0.0.8", null);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("origin_required"));
    }

    @Test
    void trustsCloudflareIpOnlyWhenProxyHeadersAreEnabled() throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, true), clock::get);

        MockHttpServletRequest first = request("10.0.0.2", "http://localhost:63342");
        first.addHeader("CF-Connecting-IP", "203.0.113.10");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());
        assertEquals(200, firstResponse.getStatus());

        MockHttpServletRequest second = request("10.0.0.2", "http://localhost:63342");
        second.addHeader("CF-Connecting-IP", "203.0.113.11");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());
        assertEquals(200, secondResponse.getStatus());
    }

    @Test
    void optionsPreflightDoesNotConsumeRateLimit() throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        RagApiSecurityFilter filter = new RagApiSecurityFilter(properties(true, false), clock::get);

        MockHttpServletRequest options = new MockHttpServletRequest("OPTIONS", "/api/rag/answers");
        options.setRemoteAddr("10.0.0.8");
        options.addHeader("Origin", "http://localhost:63342");
        MockHttpServletResponse optionsResponse = new MockHttpServletResponse();
        filter.doFilter(options, optionsResponse, new MockFilterChain());
        assertEquals(200, optionsResponse.getStatus());

        assertEquals(200, execute(filter, "10.0.0.8", "http://localhost:63342").getStatus());
    }

    private static RagApiSecurityProperties properties(boolean requireOrigin, boolean trustProxyHeaders) {
        return new RagApiSecurityProperties(
                true,
                ALLOWED_ORIGINS,
                Duration.ofSeconds(20),
                requireOrigin,
                trustProxyHeaders,
                10_000);
    }

    private static MockHttpServletResponse execute(
            RagApiSecurityFilter filter,
            String remoteIp,
            String origin) throws Exception {

        MockHttpServletRequest request = request(remoteIp, origin);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest request(String remoteIp, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rag/answers");
        request.setRemoteAddr(remoteIp);
        request.setContentType("application/json");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }
}
