package com.campusflow.service.dto;

import com.campusflow.model.enums.Role;

import java.util.Objects;

/**
 * Sanitized authentication response DTO.
 *
 * <p><strong>Security Invariant:</strong> Contains zero sensitive credentials or password hashes.</p>
 */
public record AuthResultDto(String sessionId,
                            int userId,
                            String username,
                            Role role,
                            String fullName,
                            Integer departmentId,
                            boolean mustChangePassword) {

    public AuthResultDto {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
    }
}
