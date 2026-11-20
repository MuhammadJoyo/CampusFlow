package com.campusflow.model.enums;

/**
 * Represents the three user roles in CampusFlow.
 *
 * <p>Role determines which operations a user may perform and which
 * server-side authorization scopes apply to their requests.</p>
 *
 * <p>SCD concepts: SCD-09 (OOP Inheritance), SCD-11 (Design Patterns).</p>
 *
 * @see com.campusflow.model.User
 */
public enum Role {

    STUDENT("Student"),
    STAFF("Staff Member"),
    ADMIN("Administrator");

    // ── Fields ──────────────────────────────────────────────────────────────

    private final String displayName;

    // ── Constructor ─────────────────────────────────────────────────────────

    Role(String displayName) {
        this.displayName = displayName;
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable label suitable for display in the UI.
     *
     * @return display name string
     */
    public String getDisplayName() {
        return displayName;
    }
}
