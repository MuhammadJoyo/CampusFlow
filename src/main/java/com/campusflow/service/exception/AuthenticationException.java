package com.campusflow.service.exception;

/**
 * Thrown when credentials fail or account is inactive.
 */
public class AuthenticationException extends ServiceException {

    public AuthenticationException(String message) {
        super(message);
    }
}
