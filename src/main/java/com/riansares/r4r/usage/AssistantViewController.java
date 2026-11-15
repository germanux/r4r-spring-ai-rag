package com.riansares.r4r.usage;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AssistantViewController {
    private final AssistantUsage usage;
    private long window;
    private int accepted;
    public AssistantViewController(AssistantUsage usage) { this.usage = usage; }
    // A global bound protects the optional telemetry endpoint without consuming answer quotas.
    @PostMapping("/api/rag/usage/views")
    public synchronized ResponseEntity<Void> view(HttpServletRequest request) {
        String source = AssistantUsage.origin(request.getHeader("Origin"));
        if ("UNKNOWN".equals(source)) return ResponseEntity.status(403).build();
        long now = System.nanoTime();
        if (now-window > 60_000_000_000L) { window=now; accepted=0; }
        if (++accepted > 120) return ResponseEntity.status(429).build();
        usage.view(source);
        return ResponseEntity.accepted().build();
    }
}
