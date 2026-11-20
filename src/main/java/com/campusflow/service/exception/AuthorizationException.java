package com.campusflow.service.exception;

/**
 * Thrown when an authenticated user attempts an operation outside their permitted role
 * or departmental boundary (ADR-002, NFR-07).
 */
public class AuthorizationException extends ServiceException {

    public AuthorizationException(String message) {
        super(message);
    }
}
