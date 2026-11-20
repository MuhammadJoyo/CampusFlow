package com.campusflow.service.dto;

import com.campusflow.model.enums.Role;

import java.util.Objects;

/**
 * Data transfer object for user provisioning.
 *
 * <p><strong>Security Note:</strong> The plaintext password in this DTO exists only in memory
 * during the user creation call and is immediately hashed via BCrypt before persistence.
 * It is never returned in any response or logged (SRS NFR-07, ADR-004).</p>
 */
public record UserCreateDto(String username,
                            String password,
                            Role role,
                            String fullName,
                            String email,
                            Integer departmentId) {

    public UserCreateDto {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        username = username.trim();
        fullName = fullName.trim();
        email = email.trim();
    }
}
