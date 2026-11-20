package com.campusflow.service.exception;

/**
 * Thrown when an illegal ticket status lifecycle transition is requested.
 */
public class InvalidStateTransitionException extends ServiceException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
