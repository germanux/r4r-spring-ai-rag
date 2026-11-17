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

/** Bounded technical ledger. No persistent visitor tracking; supports objections to content logging. */
@Service
public class AssistantUsage {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AssistantUsage.class);
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
        private final UUID id, viewId;
        private final String source, ip, country;
        private final boolean keepContent;
        private final long started = System.nanoTime();
        private final AtomicBoolean done = new AtomicBoolean();
        private String question;
        private Ticket(HttpServletRequest request) {
            source = request == null ? "UNKNOWN" : origin(request.getHeader("Origin"));
            boolean known = !"UNKNOWN".equals(source) && request != null;
            boolean objected = known && "1".equals(request.getHeader("X-R4R-Usage-Opt-Out"));
            keepContent = enabled && known && privacyReady && contentEnabled && !objected;
            var details = known && privacyReady ? network.publicDetails(request) : new com.riansares.r4r.config.ClientNetwork.Details(null,null);
            ip = securityEnabled && !objected ? details.address() : null;
            country = details.country();
            viewId = known ? uuid(request.getHeader("X-R4R-View-Id")) : null;
            UUID supplied = known ? uuid(request.getHeader("X-R4R-Request-Id")) : null;
            id = supplied == null ? UUID.randomUUID() : supplied;
        }
        public void question(String value) { if (keepContent) question = value; }
        public void finish(String status) { complete(status, null, null, null, null, null, null, "ERROR".equals(status)?"request_failed":null); }
        public void error(String code) {
            String safe = code != null && java.util.Set.of("timeout","cancelled","processing_failed","rate_limited","invalid_request","unavailable").contains(code) ? code : "request_failed";
            complete("ERROR",null,null,null,null,null,null,safe);
        }
        public void response(RagQueryResponse response, Double generationMs) {
            complete(response.status().name(),response.answer(),response.provider()==null?null:response.provider().name(),
                response.provider()==null?null:response.provider().model(),generationMs,uuid(response.auditId()),response.citations(),null);
        }
        private void complete(String status, String answer, String provider, String model, Double generationMs, UUID auditId,
                              Object citations, String error) {
            if (!done.compareAndSet(false, true)) return;
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            String exclusion = keepContent ? HistoryContentPolicy.exclusion(question,answer) : "disabled_or_objected";
            boolean retain = keepContent && exclusion == null;
            String sources = null;
            try { if (retain && citations != null) sources = new ObjectMapper().writeValueAsString(citations); }
            catch (java.io.IOException ignored) { }
            write("INSERT INTO assistant_usage(id,origin,event_type,outcome,duration_ms,view_id,question,answer,provider,model,generation_ms,audit_id,effective_outcome,total_duration_ms,client_ip,country_code,network_basis,content_basis,content_exclusion,citations,error_code,basis_recorded_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::inet,?,?,?,?,?::jsonb,?,now()) ON CONFLICT(id) DO NOTHING",
                id,source,"QUERY",status,duration,viewId,retain?question:null,retain?answer:null,provider,model,generationMs,auditId,status,duration,
                ip,country,ip!=null?"security-interest-v1":country!=null?"technical-geography-v1":null,
                retain?"legitimate-interest-v1":null,exclusion,sources,error);
        }
        private void view() {
            write("INSERT INTO assistant_usage(id,origin,event_type,view_id,client_ip,country_code,network_basis) VALUES (?,?,?,?,?::inet,?,?) ON CONFLICT(id) DO NOTHING",
                id,source,"VIEW",viewId,ip,country,ip!=null?"security-interest-v1":country!=null?"technical-geography-v1":null);
        }
    }
    public void view(String source) { write("INSERT INTO assistant_usage(id,origin,event_type) VALUES (?,?,?)",UUID.randomUUID(),source,"VIEW"); }
    public void view(HttpServletRequest request) { begin(request).view(); }
    public void audit(String id, RagAuditResult result) {
        UUID auditId=uuid(id);
        if(auditId==null || result==null)return;
        try {
            var json=new ObjectMapper();
            String technical=json.writeValueAsString(Map.of("conclusive",result.conclusive(),"originalStatus",result.originalStatus(),
                "recommendedStatus",result.recommendedStatus(),"effectiveStatus",result.effectiveStatus()));
            String full=json.writeValueAsString(result);
            write("UPDATE assistant_usage SET audit_result=CASE WHEN content_basis IS NOT NULL AND ? THEN ?::jsonb ELSE ?::jsonb END, " +
                "effective_outcome=?,audit_completed_at=now(),total_duration_ms=duration_ms+GREATEST(0,(extract(epoch FROM (now()-occurred_at))*1000)::bigint) " +
                "WHERE audit_id=? AND audit_completed_at IS NULL AND restricted_at IS NULL",
                HistoryContentPolicy.exclusion(full)==null,full,technical,result.effectiveStatus().name(),auditId);
        } catch(java.io.IOException ignored) { }
    }
    public void object(HttpServletRequest request, java.util.List<String> references) {
        String source=origin(request.getHeader("Origin"));
        if("UNKNOWN".equals(source))return;
        var ids=references.stream().map(AssistantUsage::uuid).filter(java.util.Objects::nonNull).toList();
        if(ids.isEmpty())return;
        var values=new java.util.ArrayList<Object>();
        for(UUID id:ids){values.add(id);values.add(source);}
        // Restriction wins over an answer still being generated. No history is returned.
        maintenance("INSERT INTO assistant_usage(id,origin,event_type,restricted_at) VALUES " +
            String.join(",",java.util.Collections.nCopies(ids.size(),"(?,?,'QUERY',now())")) +
            " ON CONFLICT(id) DO UPDATE SET restricted_at=now() WHERE assistant_usage.origin=excluded.origin",values.toArray());
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
    @PreDestroy public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException interrupted) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
