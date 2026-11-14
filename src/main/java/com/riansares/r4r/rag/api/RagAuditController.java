package com.riansares.r4r.rag.api;

import com.riansares.r4r.rag.RagAuditResult;
import com.riansares.r4r.rag.RagAuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

/** The browser starts this only after rendering ANSWER_READY. Accepts no client-supplied evidence. */
@RestController
public class RagAuditController {
    private final RagAuditService audits;
    public RagAuditController(RagAuditService audits) { this.audits = audits; }

    @PostMapping("/api/rag/audits/{id}")
    public DeferredResult<RagAuditResult> audit(@PathVariable String id, jakarta.servlet.http.HttpServletResponse http) {
        http.setHeader("Cache-Control", "no-store");
        if (!id.matches("[a-f0-9-]{36}")) { http.setStatus(404); return null; }
        var future = audits.start(id);
        if (future == null) { http.setStatus(404); return null; }
        var response = new DeferredResult<RagAuditResult>(95_000L);
        future.whenComplete((review, failure) -> {
            if (failure == null) response.setResult(review);
            else response.setErrorResult(ResponseEntity.noContent().build());
        });
        response.onTimeout(() -> { future.cancel(true); response.setErrorResult(ResponseEntity.noContent().build()); });
        response.onError(error -> future.cancel(true));
        response.onCompletion(() -> { if (!future.isDone()) future.cancel(true); });
        return response;
    }
}
