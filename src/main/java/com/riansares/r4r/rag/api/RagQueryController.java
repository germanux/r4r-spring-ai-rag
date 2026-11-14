package com.riansares.r4r.rag.api;

import com.riansares.r4r.rag.CitedRagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagQueryController {

    private final CitedRagService citedRagService;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.riansares.r4r.rag.GenerationRouter> routers;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.riansares.r4r.rag.RagAuditService audits;

    public RagQueryController(CitedRagService citedRagService) {
        this.citedRagService = citedRagService;
    }

    @PostMapping("/answers")
    public ResponseEntity<RagQueryResponse> answer(@RequestBody(required = false) RagQueryRequest request) {
        if (!RagQueryRequest.isValid(request)) {
            return ResponseEntity.badRequest().build();
        }

        var router = routers == null ? null : routers.getIfAvailable();
        if (router != null) router.clearRequestExecution();
        try {
        var captured = new java.util.concurrent.atomic.AtomicReference<com.riansares.r4r.rag.RagEvidenceSnapshot>();
        boolean auditEnabled = audits != null && audits.enabled();
        com.riansares.r4r.rag.RagResult result = auditEnabled
                ? citedRagService.answer(request.question(), request.retrievalQuestion(), request.responseLanguage(),
                    com.riansares.r4r.rag.RagProgress.silent(), captured::set)
                : request.retrievalQuestion() == null
                && request.responseLanguage() == null
                ? citedRagService.answer(request.question())
                : citedRagService.answer(request.question(), request.retrievalQuestion(), request.responseLanguage());

        RagQueryResponse response = response(result, router == null ? null : router.requestExecution());
        if (auditEnabled) response = response.withAuditId(audits.prepare(captured.get()));

        return ResponseEntity.ok(response);
        } finally { if (router != null) router.clearRequestExecution(); }
    }

    static RagQueryResponse response(com.riansares.r4r.rag.RagResult result, com.riansares.r4r.rag.GenerationRouter.Execution execution) {
        var provider = execution == null ? null : execution.provider();
        return new RagQueryResponse(result.answer(), result.abstention(), convertCitations(result.citations()),
                provider == null ? null : new RagQueryResponse.Provider(provider.name(), provider.model(),
                        "OpenAI".equals(provider.name()) ? new InferenceHost.Details("cloud", null, null, null, null)
                                : InferenceHost.identify(provider.baseUrl(), provider.model())), result.status());
    }

    private static List<RagQueryResponse.Citation> convertCitations(java.util.List<com.riansares.r4r.rag.Citation> citations) {
        return citations.stream()
                .map(c -> new RagQueryResponse.Citation(
                        c.label(),
                        c.source(),
                        c.headingPath(),
                        c.ordinal()))
                .toList();
    }
}
