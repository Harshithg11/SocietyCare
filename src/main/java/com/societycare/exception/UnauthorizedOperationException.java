package com.societycare.exception;

/** The authenticated user is not allowed to do this (mapped to HTTP 403). */
public class UnauthorizedOperationException extends RuntimeException {
    public UnauthorizedOperationException(String message) {
        super(message);
    }
}
