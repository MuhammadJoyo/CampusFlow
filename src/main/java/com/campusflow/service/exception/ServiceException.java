package com.campusflow.service.exception;

/**
 * Base unchecked runtime exception for the Service Layer.
 */
public class ServiceException extends RuntimeException {

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
