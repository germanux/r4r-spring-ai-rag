package com.riansares.r4r.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.rag.RagAuditResult;
import com.riansares.r4r.rag.api.RagQueryResponse;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Bounded, best effort usage ledger. Text and tracking require separate affirmative consent. */
@Service
public class AssistantUsage {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AssistantUsage.class);
    public static final String CONSENT_VERSION = "2026-11-v1";
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private boolean privacyReady, contentEnabled, securityEnabled;
    private com.riansares.r4r.config.ClientNetwork network = new com.riansares.r4r.config.ClientNetwork("",false);
    @Autowired void network(com.riansares.r4r.config.ClientNetwork network,
                           @Value("${r4r.usage.security-enabled:false}") boolean enabled) {
        this.network=network; this.securityEnabled=enabled;
    }
    @Autowired void privacy(@Value("${r4r.usage.privacy-ready:false}") boolean ready,
                           @Value("${r4r.usage.content-enabled:false}") boolean content) {
        privacyReady = ready; contentEnabled = content;
    }
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), r -> { Thread t = new Thread(r, "assistant-usage"); t.setDaemon(true); return t; },
            (task, executor) -> log.warn("Assistant usage queue unavailable; record omitted"));
    public AssistantUsage(JdbcTemplate jdbc, @Value("${r4r.usage.enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.enabled = enabled;
    }
    public static String origin(String value) {
        if ("https://athagon.tech".equals(value) || "https://www.athagon.tech".equals(value)) return "ATHAGON";
        if ("https://riansares4r.com".equals(value) || "https://www.riansares4r.com".equals(value)) return "RIANSARES4R";
        return "UNKNOWN";
    }
    private static UUID uuid(String value) {
        try { return value != null && value.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}") ? UUID.fromString(value) : null; }
        catch (IllegalArgumentException ignored) { return null; }
    }
    public Ticket begin() {
        var context = RequestContextHolder.getRequestAttributes();
        return begin(context instanceof ServletRequestAttributes attrs ? attrs.getRequest() : null);
    }
    public Ticket begin(HttpServletRequest request) { return new Ticket(request); }
    public final class Ticket {
        private final UUID id;
        private final String source, analytics, history;
        private final UUID visitor, session;
        private final String ip, country;
        private final long started = System.nanoTime();
        private final AtomicBoolean done = new AtomicBoolean();
        private String question;
        private Ticket(HttpServletRequest request) {
            source = request == null ? "UNKNOWN" : origin(request.getHeader("Origin"));
            boolean known = !"UNKNOWN".equals(source) && privacyReady && request != null;
            analytics = known && CONSENT_VERSION.equals(request.getHeader("X-R4R-Analytics-Consent")) ? CONSENT_VERSION : null;
            history = known && contentEnabled && CONSENT_VERSION.equals(request.getHeader("X-R4R-History-Consent")) ? CONSENT_VERSION : null;
            var details = known && securityEnabled ? network.publicDetails(request) : new com.riansares.r4r.config.ClientNetwork.Details(null,null);
            ip=details.address(); country=details.country();
            visitor = analytics == null ? null : uuid(request.getHeader("X-R4R-Visitor"));
            session = visitor == null ? null : uuid(request.getHeader("X-R4R-Session"));
            UUID supplied = request != null && !"UNKNOWN".equals(source) ? uuid(request.getHeader("X-R4R-Request-Id")) : null;
            id = supplied == null ? UUID.randomUUID() : supplied;
        }
        public void question(String value) { if (history != null) question = value; }
        public void finish(String status) { complete(status, null, null, null, null, null); }
        public void response(RagQueryResponse response, Double generationMs) {
            complete(response.status().name(), response.answer(), response.provider() == null ? null : response.provider().name(),
                    response.provider() == null ? null : response.provider().model(), generationMs, uuid(response.auditId()));
        }
        private void complete(String status, String answer, String provider, String model, Double generationMs, UUID auditId) {
            if (!done.compareAndSet(false, true)) return;
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            write("INSERT INTO assistant_usage(id,origin,event_type,outcome,duration_ms,visitor_id,session_id,analytics_consent,history_consent,consent_at,question,answer,provider,model,generation_ms,audit_id,effective_outcome,total_duration_ms,client_ip,country_code,security_basis,network_review_due_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,CASE WHEN ? THEN now() ELSE NULL END,?,?,?,?,?,?,?,?,?::inet,?,?,CASE WHEN ? THEN now()+interval '7 days' ELSE NULL END) ON CONFLICT(id) DO NOTHING",
                    id, source, "QUERY", status, duration, visitor, session, analytics, history, analytics != null || history != null,
                    question, history == null ? null : answer, provider, model, generationMs, auditId, status, duration, ip, country, ip == null ? null : "availability-abuse-v1", ip != null);
        }
        private void view() {
            write("INSERT INTO assistant_usage(id,origin,event_type,visitor_id,session_id,analytics_consent,consent_at,client_ip,country_code,security_basis,network_review_due_at) VALUES (?,?,?,?,?,?,CASE WHEN ? THEN now() ELSE NULL END,?::inet,?,?,CASE WHEN ? THEN now()+interval '7 days' ELSE NULL END) ON CONFLICT(id) DO UPDATE SET visitor_id=excluded.visitor_id,session_id=excluded.session_id,analytics_consent=excluded.analytics_consent,consent_at=excluded.consent_at WHERE assistant_usage.origin=excluded.origin AND assistant_usage.event_type='VIEW' AND assistant_usage.restricted_at IS NULL",
                    id, source, "VIEW", visitor, session, analytics, analytics != null, ip, country, ip == null ? null : "availability-abuse-v1", ip != null);
        }
    }
    public void view(String source) { write("INSERT INTO assistant_usage(id,origin,event_type) VALUES (?,?,?)", UUID.randomUUID(),source,"VIEW"); }
    public void view(HttpServletRequest request) { begin(request).view(); }
    public void audit(String id, RagAuditResult result) {
        UUID auditId = uuid(id);
        if (auditId == null || result == null) return;
        // Store structured public review only; no retrieval evidence. Free text requires history consent.
        try {
            var json = new ObjectMapper();
            String technical = json.writeValueAsString(Map.of("conclusive",result.conclusive(),"originalStatus",result.originalStatus(),
                    "recommendedStatus",result.recommendedStatus(),"effectiveStatus",result.effectiveStatus()));
            String full = json.writeValueAsString(result);
            write("UPDATE assistant_usage SET audit_result=CASE WHEN history_consent IS NOT NULL THEN ?::jsonb ELSE ?::jsonb END, " +
                    "effective_outcome=?, audit_completed_at=now(), total_duration_ms=duration_ms+GREATEST(0,(extract(epoch FROM (now()-occurred_at))*1000)::bigint) " +
                    "WHERE audit_id=? AND audit_completed_at IS NULL AND restricted_at IS NULL", full,technical,result.effectiveStatus().name(),auditId);
        } catch (java.io.IOException ignored) { }
    }
    public void withdraw(HttpServletRequest request, java.util.List<String> references, boolean analytics, boolean history) {
        String source=origin(request.getHeader("Origin"));
        if ("UNKNOWN".equals(source)) return;
        UUID visitor=uuid(request.getHeader("X-R4R-Visitor"));
        var ids=references.stream().map(AssistantUsage::uuid).filter(java.util.Objects::nonNull).toList();
        if (history && !ids.isEmpty()) {
            var tombstones=new java.util.ArrayList<Object>();
            for(UUID id:ids){tombstones.add(id);tombstones.add(source);}
            // Same query reference wins even if the in-flight answer has not been inserted yet.
            maintenance("INSERT INTO assistant_usage(id,origin,event_type,restricted_at) VALUES " +
                String.join(",",java.util.Collections.nCopies(ids.size(),"(?,?,'QUERY',now())")) +
                " ON CONFLICT(id) DO UPDATE SET restricted_at=now() WHERE assistant_usage.origin=excluded.origin AND assistant_usage.event_type='QUERY'",tombstones.toArray());
        }
        if (history && (visitor!=null || !ids.isEmpty())) {
            var values=new java.util.ArrayList<Object>(); values.add(source);values.add(visitor);
            String scope="visitor_id=?";
            if (!ids.isEmpty()) { scope+=" OR id IN ("+String.join(",",java.util.Collections.nCopies(ids.size(),"?"))+")";values.addAll(ids); }
            maintenance("UPDATE assistant_usage SET restricted_at=now() WHERE origin=? AND history_consent IS NOT NULL AND ("+scope+")",values.toArray());
        }
        if (analytics && visitor!=null) maintenance("UPDATE assistant_usage SET visitor_id=NULL,session_id=NULL,analytics_consent=NULL WHERE origin=? AND visitor_id=?",source,visitor);
    }
    private void write(String sql, Object... values) {
        if (!enabled) return;
        maintenance(sql,values);
    }
    private void maintenance(String sql, Object... values) {
        if (writer.isShutdown()) return;
        writer.execute(() -> {
            try {
                jdbc.execute((ConnectionCallback<Void>) connection -> {
                    try (var stmt = connection.prepareStatement(sql)) {
                        stmt.setQueryTimeout(2);
                        for (int i=0;i<values.length;i++) stmt.setObject(i+1,values[i]);
                        stmt.executeUpdate();
                    }
                    return null;
                });
            } catch (RuntimeException ignored) { log.warn("Assistant usage database write failed; record omitted"); }
        });
    }
    @PreDestroy public void close() { writer.shutdownNow(); }
}
