package com.riansares.r4r.rag;

@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
public final class EmbeddingUnavailableException extends RuntimeException {
    public EmbeddingUnavailableException(String message) { super(message); }
    public EmbeddingUnavailableException(String message, Throwable cause) { super(message, cause); }
}
