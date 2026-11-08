package com.riansares.r4r.vector;

@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
public final class VectorStoreUnavailableException extends RuntimeException {
    public VectorStoreUnavailableException(String message,Throwable cause) { super(message,cause); }
}
