package com.campusflow.service.exception;

/**
 * Thrown when business validation fails (e.g., note length < 10 characters, blank fields,
 * or attempting to deactivate the last remaining active administrator).
 */
public class ValidationException extends ServiceException {

    public ValidationException(String message) {
        super(message);
    }
}
