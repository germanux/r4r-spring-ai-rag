package com.riansares.r4r.usage;

import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Best effort, bounded and metadata-only. No request text enters this service. */
@Service
public class AssistantUsage {
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), r -> { Thread t = new Thread(r, "assistant-usage"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardPolicy());
    public AssistantUsage(JdbcTemplate jdbc, @Value("${r4r.usage.enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.enabled = enabled;
    }
    public static String origin(String value) {
        if ("https://athagon.tech".equals(value) || "https://www.athagon.tech".equals(value)) return "ATHAGON";
        if ("https://riansares4r.com".equals(value) || "https://www.riansares4r.com".equals(value)) return "RIANSARES4R";
        return "UNKNOWN";
    }
    public Ticket begin() {
        var context = RequestContextHolder.getRequestAttributes();
        String source = context instanceof ServletRequestAttributes attrs ? origin(attrs.getRequest().getHeader("Origin")) : "UNKNOWN";
        return new Ticket(source);
    }
    public final class Ticket {
        private final String source;
        private final long started = System.nanoTime();
        private final AtomicBoolean done = new AtomicBoolean();
        private Ticket(String source) { this.source = source; }
        public void finish(String status) {
            if (done.compareAndSet(false, true)) record(source, "QUERY", status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
        }
    }
    public void view(String source) { record(source, "VIEW", null, null); }
    private void record(String source, String type, String status, Long duration) {
        if (!enabled || writer.isShutdown()) return;
        writer.execute(() -> {
            try {
                jdbc.execute((ConnectionCallback<Void>) connection -> {
                    try (var stmt = connection.prepareStatement("INSERT INTO assistant_usage(id,origin,event_type,outcome,duration_ms) VALUES (?,?,?,?,?)")) {
                        stmt.setQueryTimeout(2);
                        stmt.setObject(1, UUID.randomUUID()); stmt.setString(2, source); stmt.setString(3, type);
                        stmt.setString(4, status); stmt.setObject(5, duration); stmt.executeUpdate();
                    }
                    return null;
                });
            } catch (RuntimeException ignored) { /* No query text or diagnostics with sensitive data in logs. */ }
        });
    }
    @PreDestroy public void close() { writer.shutdownNow(); }
}
