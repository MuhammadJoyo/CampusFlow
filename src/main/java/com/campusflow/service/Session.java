package com.campusflow.service;

import com.campusflow.model.enums.Role;
import com.campusflow.service.exception.AuthorizationException;
import com.campusflow.service.exception.PasswordChangeRequiredException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents an active authenticated user session managed in memory.
 *
 * <p>Encapsulates user identity, role, and departmental scope for server-side authorization
 * (ADR-002, ADR-003, NFR-07).</p>
 */
public class Session {

    private final String sessionId;
    private final int userId;
    private final String username;
    private final Role role;
    private final Integer departmentId;
    private final LocalDateTime createdAt;
    private volatile LocalDateTime lastAccessedAt;
    private volatile boolean passwordChangeRequired;

    public Session(String sessionId,
                   int userId,
                   String username,
                   Role role,
                   Integer departmentId,
                   boolean passwordChangeRequired) {

        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.userId = userId;
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.departmentId = departmentId;
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = this.createdAt;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public void setPasswordChangeRequired(boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    /**
     * Updates the last accessed timestamp to the current instant.
     */
    public void touch() {
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * Checks whether the session has expired due to inactivity.
     *
     * @param timeoutMinutes inactivity limit in minutes
     * @return true if expired, false otherwise
     */
    public boolean isExpired(int timeoutMinutes) {
        if (timeoutMinutes <= 0) {
            return false;
        }
        long minutesInactive = ChronoUnit.MINUTES.between(lastAccessedAt, LocalDateTime.now());
        return minutesInactive >= timeoutMinutes;
    }

    /**
     * Verifies that this session possesses one of the allowed roles.
     *
     * @param allowedRoles roles permitted for the operation
     * @throws AuthorizationException if the session role is not authorized
     */
    public void requireRole(Role... allowedRoles) {
        requireNotRequiringPasswordChange();
        for (Role allowed : allowedRoles) {
            if (this.role == allowed) {
                return;
            }
        }
        throw new AuthorizationException("Access denied for role '" + role + "'. Required: " + Arrays.toString(allowedRoles));
    }

    /**
     * Ensures that this session is not blocked awaiting mandatory password change.
     *
     * @throws PasswordChangeRequiredException if password change is pending
     */
    public void requireNotRequiringPasswordChange() {
        if (passwordChangeRequired) {
            throw new PasswordChangeRequiredException("Mandatory password change required before accessing system functions.");
        }
    }

    /**
     * Verifies departmental alignment for staff operations.
     *
     * @param targetDeptId target department ID to verify
     * @throws AuthorizationException if staff member belongs to a different department
     */
    public void requireDepartment(int targetDeptId) {
        if (role == Role.ADMIN) {
            return; // Admin bypasses department constraint
        }
        if (departmentId == null || departmentId != targetDeptId) {
            throw new AuthorizationException("Staff member (dept " + departmentId + ") is not authorized to access department " + targetDeptId);
        }
    }
}
