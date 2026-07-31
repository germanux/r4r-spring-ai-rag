package com.riansares.r4r.rag.api;

import com.riansares.r4r.rag.CitedRagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagQueryController {

    private final CitedRagService citedRagService;

    public RagQueryController(CitedRagService citedRagService) {
        this.citedRagService = citedRagService;
    }

    @PostMapping("/answers")
    public ResponseEntity<RagQueryResponse> answer(@RequestBody(required = false) RagQueryRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        com.riansares.r4r.rag.RagResult result = citedRagService.answer(request.question());
        
        List<RagQueryResponse.Citation> citations = convertCitations(result.citations());
        
        RagQueryResponse response = new RagQueryResponse(
                result.answer(),
                result.abstention(),
                citations
        );
        
        return ResponseEntity.ok(response);
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
