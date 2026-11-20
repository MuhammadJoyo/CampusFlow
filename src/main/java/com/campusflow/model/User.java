package com.campusflow.model;

import com.campusflow.model.enums.Role;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Abstract base class for all CampusFlow user accounts.
 *
 * <h2>Design Decisions</h2>
 * <ul>
 *   <li>Declared {@code abstract} because no independent use case exists for a
 *       plain {@code User} — every account is a {@code Student}, {@code StaffMember},
 *       or {@code Admin} (SCD-09 OOP Inheritance).</li>
 *   <li>All fields except {@code isActive} are {@code final} to enforce immutability
 *       after construction.</li>
 *   <li>{@code passwordHash} has a restricted getter with a Javadoc warning —
 *       it must never appear in protocol responses or logs (SRS NFR-07, ADR-004).</li>
 *   <li>{@code equals} and {@code hashCode} are based solely on {@code id} (the
 *       database primary key), so two references to the same account are always
 *       equal regardless of which fields were loaded.</li>
 * </ul>
 *
 * <p>SCD concepts: SCD-09 (Inheritance), SCD-10 (Polymorphism via role enum),
 * SCD-11 (encapsulation, immutability).</p>
 */
public abstract class User {

    // ── Fields (all final except isActive) ───────────────────────────────────

    private final int id;
    private final String username;
    private final String passwordHash;   // Never exposed in protocol responses or logs
    private final Role role;
    private final String fullName;
    private final String email;
    private boolean isActive;
    private final LocalDateTime createdAt;
    private boolean mustChangePassword;

    // ── Constructors ─────────────────────────────────────────────────────────

    /**
     * Constructs a User with all required fields including password change enforcement.
     *
     * @param id                 database primary key (≥ 0; 0 is used for transient instances)
     * @param username           unique login username (non-null, non-blank)
     * @param passwordHash       BCrypt hash of the password (non-null, non-blank)
     * @param role               user role (non-null)
     * @param fullName           display name (non-null, non-blank)
     * @param email              email address (non-null, non-blank)
     * @param isActive           whether the account is active
     * @param createdAt          account creation timestamp (non-null)
     * @param mustChangePassword whether the user must change password on next login
     * @throws NullPointerException     if any reference parameter is null
     * @throws IllegalArgumentException if username, passwordHash, fullName, or email is blank
     */
    protected User(int id,
                   String username,
                   String passwordHash,
                   Role role,
                   String fullName,
                   String email,
                   boolean isActive,
                   LocalDateTime createdAt,
                   boolean mustChangePassword) {

        Objects.requireNonNull(username,     "username must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(role,         "role must not be null");
        Objects.requireNonNull(fullName,     "fullName must not be null");
        Objects.requireNonNull(email,        "email must not be null");
        Objects.requireNonNull(createdAt,    "createdAt must not be null");

        if (username.isBlank())     throw new IllegalArgumentException("username must not be blank");
        if (passwordHash.isBlank()) throw new IllegalArgumentException("passwordHash must not be blank");
        if (fullName.isBlank())     throw new IllegalArgumentException("fullName must not be blank");
        if (email.isBlank())        throw new IllegalArgumentException("email must not be blank");

        this.id                 = id;
        this.username           = username;
        this.passwordHash       = passwordHash;
        this.role               = role;
        this.fullName           = fullName;
        this.email              = email;
        this.isActive           = isActive;
        this.createdAt          = LocalDateTime.from(createdAt);
        this.mustChangePassword = mustChangePassword;
    }

    /**
     * Backward-compatible constructor defaulting {@code mustChangePassword} to {@code false}.
     */
    protected User(int id,
                   String username,
                   String passwordHash,
                   Role role,
                   String fullName,
                   String email,
                   boolean isActive,
                   LocalDateTime createdAt) {
        this(id, username, passwordHash, role, fullName, email, isActive, createdAt, false);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** @return database primary key */
    public int getId() { return id; }

    /** @return unique login username */
    public String getUsername() { return username; }

    /** @return user role */
    public Role getRole() { return role; }

    /** @return whether this account is active */
    public boolean isActive() { return isActive; }

    /** @return full display name */
    public String getFullName() { return fullName; }

    /** @return email address */
    public String getEmail() { return email; }

    /** @return account creation timestamp (defensive copy) */
    public LocalDateTime getCreatedAt() { return LocalDateTime.from(createdAt); }

    /**
     * Returns the BCrypt password hash.
     *
     * <p><strong>Warning</strong>: this method exists solely for the repository
     * layer to persist the hash. It must never be included in protocol responses,
     * logs, or UI output (SRS NFR-07, ADR-004).</p>
     *
     * @return BCrypt hash string
     */
    public String getPasswordHash() { return passwordHash; }

    /** @return whether the user is required to change password upon login */
    public boolean isMustChangePassword() { return mustChangePassword; }

    /**
     * Sets whether this user must change password upon next login.
     *
     * @param mustChangePassword new flag value
     */
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    // ── Mutation ─────────────────────────────────────────────────────────────

    /**
     * Sets the active state of this account.
     * Called by the repository layer after a database update.
     *
     * @param active new active state
     */
    public void setActive(boolean active) { this.isActive = active; }

    // ── Object overrides ─────────────────────────────────────────────────────

    /**
     * Two User instances are equal if and only if they have the same database id.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    /**
     * Returns a safe string representation that never includes the password hash.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() +
               "{id=" + id +
               ", username='" + username + "'" +
               ", role=" + role +
               ", active=" + isActive +
               "}";
    }
}
