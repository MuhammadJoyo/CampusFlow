package com.campusflow.model;

import com.campusflow.model.enums.Role;

import java.time.LocalDateTime;

/**
 * Represents an administrator who manages users, categories, ticket overrides,
 * and views analytics reports.
 *
 * <p>Administrators have unrestricted access to all tickets and can override
 * the standard workflow constraints (SRS FR-17, ADR-002).</p>
 *
 * <p>SCD concepts: SCD-09 (OOP Inheritance — extends User).</p>
 */
public final class Admin extends User {

    /**
     * Constructs an Admin account.
     *
     * @param id           database primary key
     * @param username     unique login username
     * @param passwordHash BCrypt password hash
     * @param fullName     display name
     * @param email        email address
     * @param isActive     account active state
     * @param createdAt    creation timestamp
     */
    public Admin(int id,
                 String username,
                 String passwordHash,
                 String fullName,
                 String email,
                 boolean isActive,
                 LocalDateTime createdAt) {
        super(id, username, passwordHash, Role.ADMIN, fullName, email, isActive, createdAt);
    }
}
