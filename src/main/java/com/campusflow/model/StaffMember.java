package com.campusflow.model;

import com.campusflow.model.enums.Role;

import java.time.LocalDateTime;

/**
 * Represents a staff member (IT technician, maintenance worker, or academic admin)
 * who resolves assigned campus service tickets.
 *
 * <p>Each staff member belongs to exactly one department, identified by
 * {@code departmentId}. Staff may only update tickets assigned to them and
 * are scoped to their department's queue (SRS FR-07, FR-10).</p>
 *
 * <p>SCD concepts: SCD-09 (OOP Inheritance — extends User with additional field).</p>
 */
public final class StaffMember extends User {

    // ── Additional Field ─────────────────────────────────────────────────────

    private final int departmentId;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a StaffMember account.
     *
     * @param id           database primary key
     * @param username     unique login username
     * @param passwordHash BCrypt password hash
     * @param fullName     display name
     * @param email        email address
     * @param isActive     account active state
     * @param createdAt    creation timestamp
     * @param departmentId FK to the departments table (must be &gt; 0)
     * @throws IllegalArgumentException if departmentId ≤ 0
     */
    public StaffMember(int id,
                       String username,
                       String passwordHash,
                       String fullName,
                       String email,
                       boolean isActive,
                       LocalDateTime createdAt,
                       int departmentId) {
        super(id, username, passwordHash, Role.STAFF, fullName, email, isActive, createdAt);
        if (departmentId <= 0) {
            throw new IllegalArgumentException("departmentId must be > 0, got: " + departmentId);
        }
        this.departmentId = departmentId;
    }

    // ── Getter ───────────────────────────────────────────────────────────────

    /**
     * Returns the department this staff member belongs to.
     *
     * @return department FK (always &gt; 0)
     */
    public int getDepartmentId() {
        return departmentId;
    }
}
