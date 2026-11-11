package com.societycare.exception;

import org.springframework.http.HttpStatus;

/** A business rule was violated. Defaults to HTTP 400; the second constructor allows e.g. 409 Conflict. */
public class BusinessRuleException extends RuntimeException {

    private final HttpStatus status;

    public BusinessRuleException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BusinessRuleException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
