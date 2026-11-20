package com.campusflow.repository;

/**
 * Unchecked runtime exception thrown by repository classes when a database access
 * or persistence operation fails.
 *
 * <p>Encapsulates checked {@link java.sql.SQLException} instances, shielding higher
 * service and UI layers from low-level JDBC details (NFR-04 Maintainability).</p>
 */
public class RepositoryException extends RuntimeException {

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
