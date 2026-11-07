package com.riansares.r4r.rag.api;

import com.riansares.r4r.rag.CitedRagService;
import com.riansares.r4r.rag.GenerationRouter;
import com.riansares.r4r.rag.RagProgress;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** MVC SSE with bounded workers, no task queue, and request-owned cancellation/heartbeat/deadline. */
@RestController
public class RagStreamController implements AutoCloseable {
    private final CitedRagService service;
    private final GenerationRouter router;
    private final Duration timeout;
    private final Duration heartbeatInterval;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), runnable -> daemon(runnable, "r4r-sse-worker"));
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, r -> daemon(r, "r4r-sse-lifecycle"));
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    @Autowired
    public RagStreamController(CitedRagService service, GenerationRouter router,
            @Value("${r4r.rag.stream.timeout:180s}") Duration timeout,
            @Value("${r4r.rag.stream.heartbeat-interval:10s}") Duration heartbeatInterval) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive SSE timeout required");
        if (heartbeatInterval.toMillis() <= 0) throw new IllegalArgumentException("Positive heartbeat interval required");
        this.heartbeatInterval = heartbeatInterval;
        this.service = service;
        this.router = router;
        this.timeout = timeout;
        scheduler.setRemoveOnCancelPolicy(true);
    }
    public RagStreamController(CitedRagService service, GenerationRouter router, Duration timeout) {
        this(service, router, timeout, Duration.ofSeconds(10));
    }
    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread;
    }
    protected SseEmitter createEmitter() { return new SseEmitter(timeout.toMillis()); }

    @PostMapping(value = "/api/rag/answers/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@RequestBody(required = false) RagQueryRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()
                || request.responseLanguage() != null && !request.responseLanguage().isBlank()
                && !request.responseLanguage().equalsIgnoreCase("en") && !request.responseLanguage().equalsIgnoreCase("es")) {
            return ResponseEntity.badRequest().build();
        }
        Session session = new Session(createEmitter());
        sessions.add(session);
        session.emitter.onCompletion(session::abort);
        session.emitter.onError(error -> session.abort());
        session.emitter.onTimeout(() -> session.fail("timeout"));
        session.task = new FutureTask<>(() -> {
            router.clearRequestExecution(); // Installed and removed on this worker, never copied from the MVC thread.
            try {
                session.progress.emit("REQUEST_RECEIVED");
                var result = service.answer(request.question(), request.retrievalQuestion(), request.responseLanguage(), session.progress);
                session.progress.checkCancelled();
                var response = RagQueryController.response(result, router.requestExecution());
                var event = session.progress.event("ANSWER_READY", null, session.progress.documentsFound(), null,
                        response.abstained() ? "abstained" : "answered");
                event.put("answer", response.answer());
                event.put("abstained", response.abstained());
                event.put("citations", response.citations());
                event.put("provider", response.provider());
                event.put("metrics", session.progress.metrics());
                session.answer(event);
            } catch (CancellationException ex) { session.abort();
            } catch (RuntimeException ex) { session.fail("processing_failed");
            } finally { router.clearRequestExecution(); }
            return null;
        });
        try {
            session.heartbeat = scheduler.scheduleAtFixedRate(session::heartbeat, heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
            session.deadline = scheduler.schedule(() -> session.fail("timeout"), timeout.toMillis(), TimeUnit.MILLISECONDS);
            workers.execute(session.task);
        } catch (RejectedExecutionException ex) {
            session.abort();
            return ResponseEntity.status(503).header("Retry-After", "5").build();
        }
        return ResponseEntity.ok().header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no").body(session.emitter);
    }

    private final class Session {
        final SseEmitter emitter;
        final AtomicBoolean closed = new AtomicBoolean();
        final RagProgress progress = new RagProgress(this::send);
        final Object writes = new Object();
        volatile FutureTask<Void> task;
        volatile ScheduledFuture<?> heartbeat, deadline;
        Session(SseEmitter emitter) { this.emitter = emitter; }
        void send(Map<String, Object> event) {
            synchronized (writes) {
                progress.checkCancelled();
                if (closed.get()) throw new CancellationException();
                try { emitter.send(SseEmitter.event().name((String) event.get("stage")).data(event, MediaType.APPLICATION_JSON)); }
                catch (IOException | IllegalStateException ex) { abort(); throw new CancellationException("SSE disconnected"); }
            }
        }
        void heartbeat() {
            synchronized (writes) {
                if (closed.get()) return;
                try { emitter.send(SseEmitter.event().comment("keepalive")); }
                catch (IOException | IllegalStateException ex) { abort(); }
            }
        }
        void answer(Map<String, Object> event) {
            synchronized (writes) { send(event); finish(false); }
        }
        void fail(String code) {
            synchronized (writes) {
                if (closed.get()) return;
                try {
                    var error = progress.event("ERROR", null, progress.documentsFound(), null, code);
                    error.put("metrics", progress.metrics());
                    send(error);
                } catch (CancellationException ignored) { }
                finally { finish(true); }
            }
        }
        void abort() { finish(true); }
        void finish(boolean cancel) {
            if (!closed.compareAndSet(false, true)) return;
            if (heartbeat != null) heartbeat.cancel(false);
            if (deadline != null) deadline.cancel(false);
            if (cancel) {
                progress.cancel();
                if (task != null) task.cancel(true);
            }
            sessions.remove(this);
            emitter.complete();
        }
    }
    int activeSessions() { return sessions.size(); }
    int activeWorkers() { return workers.getActiveCount(); }
    @Override @PreDestroy public void close() {
        sessions.forEach(Session::abort);
        workers.shutdownNow(); scheduler.shutdownNow();
    }
}
