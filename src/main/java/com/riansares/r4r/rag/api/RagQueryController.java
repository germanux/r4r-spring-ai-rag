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

    public RagQueryController(CitedRagService citedRagService) {
        this.citedRagService = citedRagService;
    }

    @PostMapping("/answers")
    public ResponseEntity<RagQueryResponse> answer(@RequestBody(required = false) RagQueryRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (request.responseLanguage() != null && !request.responseLanguage().isBlank()
                && !request.responseLanguage().equalsIgnoreCase("en")
                && !request.responseLanguage().equalsIgnoreCase("es")) {
            return ResponseEntity.badRequest().build();
        }

        var router = routers == null ? null : routers.getIfAvailable();
        if (router != null) router.clearRequestExecution();
        try {
        com.riansares.r4r.rag.RagResult result = request.retrievalQuestion() == null
                && request.responseLanguage() == null
                ? citedRagService.answer(request.question())
                : citedRagService.answer(request.question(), request.retrievalQuestion(), request.responseLanguage());

        List<RagQueryResponse.Citation> citations = convertCitations(result.citations());

        RagQueryResponse response = new RagQueryResponse(
                result.answer(),
                result.abstention(),
                citations, router == null || router.requestExecution() == null || router.requestExecution().provider() == null ? null
                        : new RagQueryResponse.Provider(router.requestExecution().provider().name(), router.requestExecution().provider().model(), "OpenAI".equals(router.requestExecution().provider().name()) ? new InferenceHost.Details("cloud", null, null, null, null) : InferenceHost.identify(router.requestExecution().provider().baseUrl(), router.requestExecution().provider().model()))
        );

        return ResponseEntity.ok(response);
        } finally { if (router != null) router.clearRequestExecution(); }
    }

    private List<RagQueryResponse.Citation> convertCitations(java.util.List<com.riansares.r4r.rag.Citation> citations) {
        return citations.stream()
                .map(c -> new RagQueryResponse.Citation(
                        c.label(),
                        c.source(),
                        c.headingPath(),
                        c.ordinal()))
                .toList();
    }
}
