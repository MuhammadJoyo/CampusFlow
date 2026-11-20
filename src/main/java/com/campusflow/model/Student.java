package com.campusflow.model;

import com.campusflow.model.enums.Role;

import java.time.LocalDateTime;

/**
 * Represents a student user who can submit and track campus service tickets.
 *
 * <p>Students may only view and search their own tickets; this is enforced
 * server-side by {@code TicketRepository.search()} (ADR-002).</p>
 *
 * <p>SCD concepts: SCD-09 (OOP Inheritance — extends User).</p>
 */
public final class Student extends User {

    /**
     * Constructs a Student account with password change enforcement.
     *
     * @param id                 database primary key
     * @param username           unique login username
     * @param passwordHash       BCrypt password hash
     * @param fullName           display name
     * @param email              email address
     * @param isActive           account active state
     * @param createdAt          creation timestamp
     * @param mustChangePassword whether password change is required
     */
    public Student(int id,
                   String username,
                   String passwordHash,
                   String fullName,
                   String email,
                   boolean isActive,
                   LocalDateTime createdAt,
                   boolean mustChangePassword) {
        super(id, username, passwordHash, Role.STUDENT, fullName, email, isActive, createdAt, mustChangePassword);
    }

    /**
     * Constructs a Student account (backward-compatible, defaults mustChangePassword to false).
     */
    public Student(int id,
                   String username,
                   String passwordHash,
                   String fullName,
                   String email,
                   boolean isActive,
                   LocalDateTime createdAt) {
        this(id, username, passwordHash, fullName, email, isActive, createdAt, false);
    }
}
