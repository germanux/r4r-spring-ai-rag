package com.riansares.r4r.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

final class RagApiSecurityFilter extends OncePerRequestFilter {

    private static final String RAG_ANSWERS_PATH = "/api/rag/answers";

    private final RagApiSecurityProperties properties;
    private final Set<String> allowedOrigins;
    private final ConcurrentHashMap<String, Long> lastAcceptedRequestNanos = new ConcurrentHashMap<>();
    private final AtomicLong acceptedRequests = new AtomicLong();
    private final LongSupplier nanoTime;

    RagApiSecurityFilter(RagApiSecurityProperties properties) {
        this(properties, System::nanoTime);
    }

    RagApiSecurityFilter(RagApiSecurityProperties properties, LongSupplier nanoTime) {
        this.properties = properties;
        this.allowedOrigins = new HashSet<>(properties.allowedOrigins());
        this.nanoTime = nanoTime;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath == null || contextPath.isEmpty()
                ? requestUri
                : requestUri.substring(contextPath.length());

        return !RAG_ANSWERS_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!originAllowed(request, response)) {
            return;
        }

        String clientIp = resolveClientIp(request);
        long now = nanoTime.getAsLong();
        long intervalNanos = properties.minInterval().toNanos();

        if (intervalNanos > 0) {
            pruneExpiredEntriesIfNeeded(now, intervalNanos);

            if (!lastAcceptedRequestNanos.containsKey(clientIp)
                    && lastAcceptedRequestNanos.size() >= properties.maxTrackedIps()) {
                writeJsonError(
                        response,
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "rate_limit_capacity",
                        "Hay demasiadas IP activas en la ventana de rate limit. Inténtalo de nuevo en unos segundos.");
                return;
            }

            AtomicLong retryAfterNanos = new AtomicLong(0);

            lastAcceptedRequestNanos.compute(clientIp, (ip, previousRequestNanos) -> {
                if (previousRequestNanos == null || now - previousRequestNanos >= intervalNanos) {
                    return now;
                }

                retryAfterNanos.set(intervalNanos - (now - previousRequestNanos));
                return previousRequestNanos;
            });

            long retryNanos = retryAfterNanos.get();
            if (retryNanos > 0) {
                long retrySeconds = Math.max(1, (long) Math.ceil(retryNanos / 1_000_000_000d));
                response.setHeader("Retry-After", Long.toString(retrySeconds));
                response.setHeader(
                        "X-R4R-RateLimit-Min-Interval-Seconds",
                        Long.toString(Math.max(1, properties.minInterval().toSeconds())));
                writeJsonError(
                        response,
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "rate_limited",
                        "Solo se permite una consulta por IP cada "
                                + humanReadable(properties.minInterval())
                                + ".");
                return;
            }

            acceptedRequests.incrementAndGet();
        }

        filterChain.doFilter(request, response);
    }

    private boolean originAllowed(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String origin = request.getHeader("Origin");

        if (origin == null || origin.isBlank()) {
            if (!properties.requireOrigin()) {
                return true;
            }

            writeJsonError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "origin_required",
                    "La API requiere una cabecera Origin permitida.");
            return false;
        }

        if (!allowedOrigins.contains(origin)) {
            writeJsonError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "origin_not_allowed",
                    "Origen no permitido.");
            return false;
        }

        // El filtro puede responder 429 antes de que Spring MVC aplique CORS.
        // Añadimos estos headers aquí para que el navegador pueda leer el 429/Retry-After.
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.addHeader("Vary", "Origin");
        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.trustProxyHeaders()) {
            String cloudflareIp = firstHeaderValue(request, "CF-Connecting-IP");
            if (cloudflareIp != null) {
                return cloudflareIp;
            }

            String forwardedFor = firstHeaderValue(request, "X-Forwarded-For");
            if (forwardedFor != null) {
                int comma = forwardedFor.indexOf(',');
                return comma >= 0 ? forwardedFor.substring(0, comma).trim() : forwardedFor;
            }
        }

        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private static String firstHeaderValue(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            return null;
        }

        value = value.trim();
        return value.length() > 128 ? value.substring(0, 128) : value;
    }

    private void pruneExpiredEntriesIfNeeded(long now, long intervalNanos) {
        long accepted = acceptedRequests.get();
        if (lastAcceptedRequestNanos.size() < properties.maxTrackedIps() / 2 && accepted % 128 != 0) {
            return;
        }

        lastAcceptedRequestNanos.entrySet().removeIf(entry -> now - entry.getValue() >= intervalNanos);
    }

    private static String humanReadable(Duration duration) {
        if (duration.toMillis() % 1_000 == 0) {
            return duration.toSeconds() + " segundos";
        }
        return duration.toMillis() + " ms";
    }

    private static void writeJsonError(
            HttpServletResponse response,
            int status,
            String error,
            String message) throws IOException {

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(
                "{\"error\":\"" + jsonEscape(error) + "\",\"message\":\"" + jsonEscape(message) + "\"}");
    }

    private static String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
