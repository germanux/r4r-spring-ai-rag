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

/** MVC SSE: bounded admission, computation and ordered delivery; lifecycle timers never write sockets. */
@RestController
public class RagStreamController implements AutoCloseable {
    private static final int MAX_STREAMS = 4;
    private final CitedRagService service;
    private final GenerationRouter router;
    private final Duration timeout;
    private final Duration heartbeatInterval;
    private final Semaphore admission = new Semaphore(MAX_STREAMS);
    private final ThreadPoolExecutor workers = pool("r4r-sse-worker");
    private final ThreadPoolExecutor writers = pool("r4r-sse-writer");
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, r -> daemon(r, "r4r-sse-lifecycle"));
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean stopping = new AtomicBoolean();

    @Autowired
    public RagStreamController(CitedRagService service, GenerationRouter router,
            @Value("${r4r.rag.stream.timeout:180s}") Duration timeout,
            @Value("${r4r.rag.stream.heartbeat-interval:10s}") Duration heartbeatInterval) {
        if (timeout.toMillis() <= 0) throw new IllegalArgumentException("Positive SSE timeout required");
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
    private static ThreadPoolExecutor pool(String name) {
        return new ThreadPoolExecutor(MAX_STREAMS, MAX_STREAMS, 0, TimeUnit.SECONDS,
                new SynchronousQueue<>(), runnable -> daemon(runnable, name));
    }
    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread;
    }
    protected SseEmitter createEmitter() { return new SseEmitter(timeout.toMillis()); }

    @PostMapping(value = "/api/rag/answers/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@RequestBody(required = false) RagQueryRequest request) {
        if (!RagQueryRequest.isValid(request)) {
            return ResponseEntity.badRequest().build();
        }
        if (stopping.get() || !admission.tryAcquire()) return busy();
        Session session;
        try { session = new Session(createEmitter()); }
        catch (RuntimeException ex) { admission.release(); throw ex; }
        sessions.add(session);
        // Create ownership before registering callbacks: a connection may already have closed.
        session.delivery = new FutureTask<>(session::deliver, null) {
            @Override protected void done() {
                if (!session.deliveryStarted.get()) session.release();
            }
        };
        session.task = new FutureTask<>(() -> {
            router.clearRequestExecution();
            try {
                session.progress.emit("REQUEST_RECEIVED");
                var result = service.answer(request.question(), request.retrievalQuestion(), request.responseLanguage(), session.progress);
                session.progress.checkCancelled();
                var response = RagQueryController.response(result, router.requestExecution());
                var event = session.progress.event("ANSWER_READY", null, session.progress.documentsFound(), null,
                        response.abstained() ? "abstained" : "answered");
                event.put("status", response.status());
                event.put("answer", response.answer()); event.put("abstained", response.abstained());
                event.put("citations", response.citations()); event.put("provider", response.provider());
                event.put("metrics", session.progress.metrics());
                session.terminal(event);
            } catch (CancellationException ex) {
                if (!session.terminalQueued.get()) session.abort();
            } catch (RuntimeException ex) { session.fail("processing_failed");
            } finally { router.clearRequestExecution(); }
            return null;
        });
        session.emitter.onCompletion(session::abort);
        session.emitter.onError(error -> session.abort());
        session.emitter.onTimeout(() -> session.fail("timeout"));
        try {
            session.heartbeat = scheduler.scheduleAtFixedRate(session::heartbeat, heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
            session.deadline = scheduler.schedule(() -> session.fail("timeout"), timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (session.closed.get()) session.abort();
            else {
                writers.execute(session.delivery);
                workers.execute(session.task);
            }
        } catch (RejectedExecutionException ex) {
            session.abort();
            return busy();
        }
        return ResponseEntity.ok().header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no").body(session.emitter);
    }
    private ResponseEntity<SseEmitter> busy() { return ResponseEntity.status(503).header("Retry-After", "5").build(); }

    private record Packet(Map<String, Object> event, boolean terminal) { }
    private final class Session {
        final SseEmitter emitter;
        final AtomicBoolean closed = new AtomicBoolean(), released = new AtomicBoolean();
        final AtomicBoolean deliveryStarted = new AtomicBoolean(), terminalQueued = new AtomicBoolean();
        final BlockingQueue<Packet> output = new ArrayBlockingQueue<>(64);
        final RagProgress progress = new RagProgress(this::send);
        volatile FutureTask<Void> task, delivery;
        volatile ScheduledFuture<?> heartbeat, deadline, forcedClose;
        Session(SseEmitter emitter) { this.emitter = emitter; }
        void send(Map<String, Object> event) {
            progress.checkCancelled();
            if (closed.get() || terminalQueued.get() || !output.offer(new Packet(event, false))) {
                abort(); throw new CancellationException("SSE disconnected or output full");
            }
        }
        void terminal(Map<String, Object> event) {
            if (!closed.get() && terminalQueued.compareAndSet(false, true)) {
                if (!output.offer(new Packet(event, true))) abort();
            }
        }
        void heartbeat() {
            if (!closed.get() && !terminalQueued.get() && output.isEmpty()) output.offer(new Packet(null, false));
        }
        void fail(String code) {
            if (closed.get()) return;
            if (terminalQueued.get()) {
                if ("timeout".equals(code)) abort();
                return;
            }
            var error = progress.event("ERROR", null, progress.documentsFound(), null, code);
            error.put("metrics", progress.metrics());
            terminal(error);
            progress.cancel();
            task.cancel(true);
            // Even an unresponsive socket cannot make the lifecycle thread wait on an emitter monitor.
            if (!closed.get()) {
                try { forcedClose = scheduler.schedule(this::abort, 100, TimeUnit.MILLISECONDS); }
                catch (RejectedExecutionException ignored) { abort(); }
                if (closed.get()) cancelTimers();
            }
        }
        void deliver() {
            deliveryStarted.set(true);
            try {
                while (!closed.get()) {
                    Packet packet = output.take();
                    if (closed.get()) break;
                    if (packet.event() == null) emitter.send(SseEmitter.event().comment("keepalive"));
                    else emitter.send(SseEmitter.event().name((String)packet.event().get("stage")).data(packet.event(), MediaType.APPLICATION_JSON));
                    if (packet.terminal()) break;
                }
            } catch (InterruptedException ex) { Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException ignored) { // Never transmit exception text.
            } finally {
                abort();
                release();
            }
        }
        void cancelTimers() {
            if (heartbeat != null) heartbeat.cancel(false);
            if (deadline != null) deadline.cancel(false);
            if (forcedClose != null) forcedClose.cancel(false);
        }
        void abort() {
            closed.set(true);
            progress.cancel();
            cancelTimers(); // Repeatable: also removes timers assigned after an early callback.
            if (task != null) task.cancel(true);
            if (delivery != null) delivery.cancel(true);
            output.clear();
        }
        void release() {
            if (!released.compareAndSet(false, true)) return;
            try { emitter.complete(); }
            catch (IllegalStateException ignored) { }
            finally {
                // Hold admission until the writer actually leaves blocking I/O, not merely until cancellation.
                sessions.remove(this);
                admission.release();
            }
        }
    }
    int activeSessions() { return sessions.size(); }
    int activeWorkers() { return workers.getActiveCount(); }
    int scheduledTasks() { return scheduler.getQueue().size(); }
    @Override @PreDestroy public void close() {
        stopping.set(true);
        sessions.forEach(Session::abort);
        workers.shutdownNow(); writers.shutdownNow(); scheduler.shutdownNow();
    }
}
