package com.campusflow.service.exception;

/**
 * Thrown when an authenticated user (such as the default administrator) attempts any operation
 * before changing their mandatory default/temporary password.
 */
public class PasswordChangeRequiredException extends ServiceException {

    public PasswordChangeRequiredException(String message) {
        super(message);
    }
}
