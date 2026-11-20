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
     * Constructs a Student account.
     *
     * @param id           database primary key
     * @param username     unique login username
     * @param passwordHash BCrypt password hash
     * @param fullName     display name
     * @param email        email address
     * @param isActive     account active state
     * @param createdAt    creation timestamp
     */
    public Student(int id,
                   String username,
                   String passwordHash,
                   String fullName,
                   String email,
                   boolean isActive,
                   LocalDateTime createdAt) {
        super(id, username, passwordHash, Role.STUDENT, fullName, email, isActive, createdAt);
    }
}
