package com.campusflow.service.exception;

/**
 * Thrown when an expected entity (ticket, user, category, or department) does not exist.
 */
public class ResourceNotFoundException extends ServiceException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
